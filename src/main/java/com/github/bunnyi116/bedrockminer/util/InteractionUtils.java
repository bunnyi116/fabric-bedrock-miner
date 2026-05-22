package com.github.bunnyi116.bedrockminer.util;

import com.github.bunnyi116.bedrockminer.mixin.MultiPlayerGameModeAccessor;
import com.github.bunnyi116.bedrockminer.mixin_extension.BlockBreakResult;
import com.github.bunnyi116.bedrockminer.mixin_extension.MultiPlayerGameModeExtension;
import lombok.Getter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Queue;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;
import static net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.*;


@SuppressWarnings("UnusedReturnValue")
@Environment(EnvType.CLIENT)
public class InteractionUtils {
    private static final Queue<BlockPos> blockQueue = new ArrayDeque<>();

    public static boolean isCurrentBreaking() {
        MultiPlayerGameModeAccessor accessor = (MultiPlayerGameModeAccessor) gameMode;
        return accessor.isDestroying();
    }

    private static int getBlockBreakingProgress() {
        MultiPlayerGameModeAccessor accessor = (MultiPlayerGameModeAccessor) gameMode;
        return accessor.getDestroyProgress() > 0.0F ? (int) (accessor.getDestroyProgress() * 10.0F) : -1;
    }

    public static BlockBreakResult updateBlockBreakingProgress(BlockPos pos, Direction direction, boolean localPrediction) {
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return BlockBreakResult.FAILED;
        }
        if (player.blockActionRestricted(level, pos, gameType)) {
            return BlockBreakResult.FAILED;
        }
        if (!PlayerUtils.canInteractWithBlockAt(pos, 1F)) {
            return BlockBreakResult.FAILED;
        }

        BlockState blockState = level.getBlockState(pos);
        // 创造直接破坏
        if (player.getAbilities().instabuild) {
            NetworkUtils.sendPacket((sequence) -> {
                if (!blockState.isAir() && localPrediction) {
                    gameMode.destroyBlock(pos);
                }
                return new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
            });
            return BlockBreakResult.COMPLETED;
        }
        MultiPlayerGameModeExtension extension = (MultiPlayerGameModeExtension) gameMode;
        extension.bedrockminer$ensureHasSentCarriedItem();
        MultiPlayerGameModeAccessor accessor = (MultiPlayerGameModeAccessor) gameMode;
        if (accessor.isDestroying() && pos.equals(accessor.getDestroyBlockPos())) {
            if (blockState.isAir()) {
                accessor.setDestroying(false);
                return BlockBreakResult.COMPLETED;
            }
//            float progress = accessor.getDestroyProgress() + blockState.getDestroyProgress(player, level, pos);
            float progress = accessor.getDestroyProgress() + PlayerUtils.calcBlockBreakingDelta(blockState);
            accessor.setDestroyProgress(progress);
            if (progress >= 1.0F) {
                NetworkUtils.sendPacket((sequence) -> {
                    if (!blockState.isAir() && localPrediction) {
                        gameMode.destroyBlock(pos);
                    }
                    return new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                });
                accessor.setDestroyProgress(-1);
                level.destroyBlockProgress(player.getId(), pos, -1);
                return BlockBreakResult.COMPLETED;
            } else {
                level.destroyBlockProgress(player.getId(), pos, getBlockBreakingProgress());
            }
        } else {
            if (accessor.isDestroying() && !pos.equals(accessor.getDestroyBlockPos())) {
                NetworkUtils.sendPacket(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, accessor.getDestroyBlockPos(), direction));
                accessor.setDestroying(false);
            }
            float progress = PlayerUtils.calcBlockBreakingDelta(blockState);
            if (progress >= 1.0F) {
                NetworkUtils.sendPacket((sequence) -> {
                    if (!blockState.isAir() && localPrediction) {
                        gameMode.destroyBlock(pos);
                    }
                    return new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                });
                return BlockBreakResult.COMPLETED;
            }
            // 仅对70%做瞬间破坏, 双位持续破坏比较麻烦，暂时不使用
            if (progress >= 0.7F) {
                NetworkUtils.sendPacket((sequence) -> new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence));
                NetworkUtils.sendPacket((sequence) -> {
                    if (!blockState.isAir() && localPrediction) {
                        gameMode.destroyBlock(pos);
                    }
                    return new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                });
                return BlockBreakResult.COMPLETED;
            }
            NetworkUtils.sendPacket((sequence) -> {
                if (!blockState.isAir() && accessor.getDestroyProgress() == 0.0F) {
                    blockState.attack(level, pos, player);
                }
                accessor.setDestroying(true);
                accessor.setDestroyProgress(0);
                accessor.setDestroyBlockPos(pos);
                level.destroyBlockProgress(player.getId(), pos, getBlockBreakingProgress());
                return new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
            });
            return BlockBreakResult.IN_PROGRESS;
        }
        return BlockBreakResult.FAILED;
    }

    public static BlockBreakResult updateBlockBreakingProgress(BlockPos pos, Direction direction) {
        return updateBlockBreakingProgress(pos, direction, true);
    }

    public static BlockBreakResult updateBlockBreakingProgress(BlockPos pos, boolean localPrediction) {
        return updateBlockBreakingProgress(pos, PlayerUtils.getClosestFace(pos), localPrediction);
    }

    public static BlockBreakResult updateBlockBreakingProgress(BlockPos pos) {
        return updateBlockBreakingProgress(pos, true);
    }

    public static void placement(BlockPos blockPos, Direction facing, @Nullable Item... items) {
        if (blockPos == null || facing == null)
            return;

        if (!BlockUtils.isReplaceable(level.getBlockState(blockPos)))
            return;

        if (!PlayerUtils.canInteractWithBlockAt(blockPos, 0F)) {
            return;
        }
        if (items != null) {
            InventoryUtils.switchToItem(items);
        }
        // 发送修改视角数据包
        if (facing.getAxis().isVertical()) {
            var yaw = switch (facing) {
                case SOUTH -> 180F;
                case EAST -> 90F;
                case NORTH -> 0F;
                case WEST -> -90F;
                default -> player.getYRot();
            };
            var pitch = switch (facing) {
                case UP -> 90F;
                case DOWN -> -90F;
                default -> 0F;
            };
            PlayerLookUtils.sendLookPacket(yaw, pitch);
        }
        // 模拟选中位置(凭空放置)
        var hitPos = blockPos.relative(facing.getOpposite());
        Vec3 hitVec3d = Vec3.atCenterOf(hitPos).relative(facing, 0.5F);   // 放置面中心坐标
        var hitResult = new BlockHitResult(hitVec3d, facing, blockPos, false);
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
    }

    public static void placement(BlockPos blockPos, Direction facing) {
        placement(blockPos, facing, (Item) null);
    }

    public static boolean canPlace(ClientLevel world, BlockPos blockPos, BlockState placeBlockState) {
        // 目标位置的方块是否可以被替换
        if (!BlockUtils.isReplaceable(world.getBlockState(blockPos))) {
            return false;
        }
        // 检查放置方块的碰撞体积
        VoxelShape collisionShape = placeBlockState.getCollisionShape(world, blockPos);
        if (collisionShape.isEmpty()) {
            return true; // 放置的方块是没有没有碰撞体积，可以放置
        }
        for (Entity entity : world.entitiesForRendering()) {
            if (entity instanceof ItemEntity) {
                return true;
            }
            if (entity.isColliding(blockPos, placeBlockState)) {
                return false;
            }
        }
        return true;
    }
}