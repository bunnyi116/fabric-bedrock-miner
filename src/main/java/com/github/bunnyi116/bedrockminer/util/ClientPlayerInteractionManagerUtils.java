package com.github.bunnyi116.bedrockminer.util;

import com.github.bunnyi116.bedrockminer.util.network.NetworkUtils;
import com.github.bunnyi116.bedrockminer.util.player.PlayerUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

@Environment(EnvType.CLIENT)
public class ClientPlayerInteractionManagerUtils {
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
        ClientPlayerInteractionManagerUtils.breakingBlock = breakingBlock;
        interactionManager.breakingBlock = breakingBlock;
    }
}
