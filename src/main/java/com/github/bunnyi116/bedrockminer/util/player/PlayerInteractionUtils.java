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

import java.util.ArrayDeque;
import java.util.Queue;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;


@Environment(EnvType.CLIENT)
public class PlayerInteractionUtils {
    public static final float BREAKING_PROGRESS_MAX = 1.0F;
    private static BlockPos currentBreakingPos = new BlockPos(-1, -1, -1);
    private static float currentBreakingProgress;
    private static boolean breakingBlock;
    private static int breakingTicks;
    private static int breakingTickMax;

    private static final Queue<BlockPos> blockQueue = new ArrayDeque<>();

    private static int getBlockBreakingProgress() {
        float breakingProgress = currentBreakingProgress >= BREAKING_PROGRESS_MAX ? 1.0F : currentBreakingProgress;
        return breakingProgress > 0.0F ? (int) (breakingProgress * 10.0F) : -1;
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos, Direction direction, boolean localPrediction) {
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }
        if (player.isBlockBreakingRestricted(world, pos, gameMode)) {
            return false;
        }
        if (!player.canInteractWithBlockAt(pos, 0F)) {
            return false;
        }
        BlockState blockState = world.getBlockState(pos);
        if (gameMode.isCreative()) {    // 创造模式下
            setBreakingBlock(true);
            NetworkUtils.sendSequencedPacket((sequence) -> { // 只需要发送START包，因为它是瞬间破坏的
                if (!blockState.isAir() && localPrediction) {
                    interactionManager.breakBlock(pos);
                }
                return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
            });
            setBreakingBlock(false);
            return true;
        }
        if (breakingBlock && pos.equals(currentBreakingPos)) {
            if (blockState.isAir()) {
                setBreakingBlock(false);
                return true;
            }
            currentBreakingProgress += PlayerUtils.calcBlockBreakingDelta(blockState);
            if (currentBreakingProgress >= BREAKING_PROGRESS_MAX) {
                NetworkUtils.sendSequencedPacket((sequence) -> {
                    if (!blockState.isAir() && localPrediction) {
                        interactionManager.breakBlock(pos);
                    }
                    return new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                });
                currentBreakingProgress = 0.0F;
                world.setBlockBreakingInfo(player.getId(), currentBreakingPos, -1);
                setBreakingBlock(false);
                return true;
            } else {
                world.setBlockBreakingInfo(player.getId(), currentBreakingPos, getBlockBreakingProgress());
            }
            ++breakingTickMax;
        } else {
            if (breakingBlock && !pos.equals(currentBreakingPos)) {
                NetworkUtils.sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, currentBreakingPos, direction));
                setBreakingBlock(false);
            }
            currentBreakingProgress += PlayerUtils.calcBlockBreakingDelta(blockState);
            if (currentBreakingProgress >= BREAKING_PROGRESS_MAX) {
                setBreakingBlock(true);
                NetworkUtils.sendSequencedPacket((sequence) -> {
                    if (!blockState.isAir() && localPrediction) {
                        interactionManager.breakBlock(pos);
                    }
                    return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                });
                setBreakingBlock(false);
                return true;
            } else {
                NetworkUtils.sendSequencedPacket((sequence) -> {
                    if (!blockState.isAir() && currentBreakingProgress == 0.0F) {
                        blockState.onBlockBreakStart(world, pos, player);
                    }
                    setBreakingBlock(true);
                    currentBreakingPos = pos;
                    currentBreakingProgress = 0.0F;
                    world.setBlockBreakingInfo(player.getId(), currentBreakingPos, getBlockBreakingProgress());
                    return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                });
            }
        }
        return false;
    }

    public static void updateBlockBreakingProgress(BlockPos pos, Direction direction) {
        updateBlockBreakingProgress(pos, direction, true);
    }

    public static void updateBlockBreakingProgress(BlockPos pos, boolean localPrediction) {
        updateBlockBreakingProgress(pos, PlayerUtils.getClosestFace(pos), localPrediction);
    }

    public static void updateBlockBreakingProgress(BlockPos pos) {
        updateBlockBreakingProgress(pos, true);
    }

    public static void resetBreaking() {
        breakingTicks = 0;
        breakingTickMax = 20;
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
    }

    public static void placement(BlockPos blockPos, Direction facing, @Nullable Item... items) {
        if (blockPos == null || facing == null)
            return;

        if (!BlockUtils.isReplaceable(world.getBlockState(blockPos)))
            return;

        if (!PlayerUtils.canInteractWithBlockAt(blockPos, 0F)) {
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
            PlayerLookUtils.sendLookPacket(networkHandler, yaw, pitch);
        }
        // 模拟选中位置(凭空放置)
        var hitPos = blockPos.offset(facing.getOpposite());
        var hitVec3d = Vec3d.ofCenter(hitPos).offset(facing, 0.5F);   // 放置面中心坐标
        var hitResult = new BlockHitResult(hitVec3d, facing, blockPos, false);
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
