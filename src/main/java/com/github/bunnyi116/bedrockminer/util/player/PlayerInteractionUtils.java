package com.github.bunnyi116.bedrockminer.util.player;

import com.github.bunnyi116.bedrockminer.util.block.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.network.NetworkUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

@Environment(EnvType.CLIENT)
public class PlayerInteractionUtils {
    public static final float BREAKING_PROGRESS_MAX = 0.7F;
    private static boolean breakingBlock;
    private static int breakingTicks;
    private static int breakingTickMax;

    public static boolean attackBlock(BlockPos pos, Direction direction, @Nullable Runnable beforeBreaking, @Nullable Runnable afterBreaking) {
        interactionManager.syncSelectedSlot();
        if (player.isBlockBreakingRestricted(world, pos, gameMode)) {
            return false;
        }
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }
        if (gameMode.isCreative()) {
            setBreakingBlock(true);
            NetworkUtils.sendSequencedPacket((sequence) -> {
                interactionManager.breakBlock(pos);
                return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
            }, beforeBreaking, afterBreaking);
            setBreakingBlock(false);
        } else if (!(breakingBlock || interactionManager.breakingBlock) || !interactionManager.isCurrentlyBreaking(pos)) {
            if ((breakingBlock || interactionManager.breakingBlock)) {
                networkHandler.sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, interactionManager.currentBreakingPos, direction));
                setBreakingBlock(false);
            }
            BlockState blockState = world.getBlockState(pos);
            float calcBlockBreakingDelta = PlayerUtils.calcBlockBreakingDelta(blockState);
            if (calcBlockBreakingDelta >= BREAKING_PROGRESS_MAX) {
                setBreakingBlock(true);
                NetworkUtils.sendSequencedPacket((sequence) -> {
                    if (!blockState.isAir()) {
                        interactionManager.breakBlock(pos);
                    }
                    return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                }, beforeBreaking, afterBreaking);
                setBreakingBlock(false);
            } else {
                NetworkUtils.sendSequencedPacket((sequence) -> {
                    if (!blockState.isAir() && interactionManager.currentBreakingProgress == 0.0F) {
                        blockState.onBlockBreakStart(world, pos, player);
                    }
                    setBreakingBlock(true);
                    interactionManager.currentBreakingPos = pos;
                    interactionManager.selectedStack = player.getMainHandStack();
                    interactionManager.currentBreakingProgress = 0.0F;
                    world.setBlockBreakingInfo(player.getId(), interactionManager.currentBreakingPos, getBlockBreakingProgress());
                    return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                });
            }
        }
        ++breakingTickMax;
        return true;
    }

    public static boolean attackBlock(BlockPos pos) {
        return attackBlock(pos, PlayerUtils.getClosestFace(pos), null, null);
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos, Direction direction, @Nullable Runnable beforeBreaking, @Nullable Runnable afterBreaking) {
        interactionManager.syncSelectedSlot();
        if (gameMode.isCreative() && world.getWorldBorder().contains(pos)) {
            setBreakingBlock(true);
            NetworkUtils.sendSequencedPacket((sequence) -> {
                interactionManager.breakBlock(pos);
                return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
            }, beforeBreaking, afterBreaking);
            setBreakingBlock(false);
            ++breakingTickMax;
            return true;
        }
        if ((breakingBlock || interactionManager.breakingBlock) && interactionManager.isCurrentlyBreaking(pos)) {
            BlockState blockState = world.getBlockState(pos);
            if (blockState.isAir()) {
                setBreakingBlock(false);
                return false;
            }
            setBreakingBlock(true);
            interactionManager.currentBreakingProgress += PlayerUtils.calcBlockBreakingDelta(blockState);
            if (interactionManager.currentBreakingProgress >= BREAKING_PROGRESS_MAX) {
                NetworkUtils.sendSequencedPacket((sequence) -> {
                    interactionManager.breakBlock(pos);
                    return new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                }, beforeBreaking, afterBreaking);
                interactionManager.currentBreakingProgress = 0.0F;
                setBreakingBlock(false);
            }
            world.setBlockBreakingInfo(player.getId(), interactionManager.currentBreakingPos, getBlockBreakingProgress());
            ++breakingTickMax;
            return true;
        }
        return attackBlock(pos, direction, beforeBreaking, afterBreaking);
    }

    public static void updateBlockBreakingProgress(BlockPos pos) {
        updateBlockBreakingProgress(pos, PlayerUtils.getClosestFace(pos), null, null);
    }

    public static int getBlockBreakingProgress() {
        return interactionManager.currentBreakingProgress > 0.0F ? (int) (interactionManager.currentBreakingProgress * 10.0F) : -1;
    }

    public static void resetBreaking() {
        breakingTicks = 0;
        breakingTickMax = 200;
        setBreakingBlock(false);
    }

    public static void autoResetBreaking() {
        if (!breakingBlock && breakingTicks > 0) {  // 如果未在破坏, 但是破坏TICK已有累计, 先进行初始化
            resetBreaking();
        }
        if (breakingBlock && breakingTicks++ > breakingTickMax) {
            resetBreaking();
        }
    }

    public static boolean isBreakingBlock() {
        return breakingBlock;
    }

    public static void setBreakingBlock(boolean breakingBlock) {
        PlayerInteractionUtils.breakingBlock = breakingBlock;
        interactionManager.breakingBlock = breakingBlock;
    }


    public static void placement(BlockPos blockPos, Direction facing, @Nullable Item... items) {
        if (blockPos == null || facing == null)
            return;

        if (!BlockUtils.isReplaceable(world.getBlockState(blockPos)))
            return;

        if (!PlayerUtils.canInteractWithBlockAt(blockPos, 1.0F)) {
            return;
        }
        if (items != null) {
            PlayerInventoryUtils.switchToItem(items);
        }

        // 发送修改视角数据包
        if (facing.getAxis().isVertical()) {
            var yaw = switch (facing) {
                case SOUTH -> 180F;
                case EAST -> 90F;
                case NORTH -> 0F;
                case WEST -> -90F;
                default -> player.getYaw();
            };
            var pitch = switch (facing) {
                case UP -> 90F;
                case DOWN -> -90F;
                default -> 0F;
            };
            PlayerLookManager.sendLookPacket(networkHandler, yaw, pitch);
        }

        // 模拟选中位置(凭空放置)
        var hitPos = blockPos.offset(facing.getOpposite());
        var hitVec3d = Vec3d.ofCenter(hitPos).offset(facing, 0.5F);   // 放置面中心坐标
        var hitResult = new BlockHitResult(hitVec3d, facing, blockPos, false);

        // 发送交互方块数据包
        interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
    }

    public static void placement(BlockPos blockPos, Direction facing) {
        placement(blockPos, facing, (Item) null);
    }

    public static boolean canPlace(ClientWorld world, BlockPos blockPos, BlockState placeBlockState) {
        // 目标位置的方块是否可以被替换
        if (!BlockUtils.isReplaceable(world.getBlockState(blockPos))) {
            return false;
        }
        // 检查放置方块的碰撞体积
        var collisionShape = placeBlockState.getCollisionShape(world, blockPos);
        if (collisionShape.isEmpty()) {
            return true; // 放置的方块是没有没有碰撞体积，可以放置
        }
        for (var entity : world.getEntities()) {
            if (entity instanceof ItemEntity) {
                return true;
            }
            if (entity.collidesWithStateAtPos(blockPos, placeBlockState)) {
                return false;
            }
        }
        return true;
    }
}
