package com.github.bunnyi116.bedrockminer.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.item.ItemStack;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

@Environment(EnvType.CLIENT)
public class ClientPlayerInteractionManagerUtils {  // 该类是为后续开发做准备
    public static final float BREAKING_PROGRESS_MAX = 0.7F;

    private static BlockPos currentBreakingPos = new BlockPos(-1, -1, -1);
    private static ItemStack selectedStack;
    private static float currentBreakingProgress;
    private static boolean breakingBlock;
    private static int lastSelectedSlot;
    private static int breakingTicks;
    private static int breakingTickMax;

    private static void syncSelectedSlot() {
        int i = player.getInventory().getSelectedSlot();
        if (i != lastSelectedSlot) {
            lastSelectedSlot = i;
            networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(lastSelectedSlot));
        }
    }

    private static boolean isCurrentlyBreaking(BlockPos pos) {
        ItemStack itemStack = player.getMainHandStack();
        return pos.equals(currentBreakingPos) && ItemStack.areItemsAndComponentsEqual(itemStack, selectedStack);
    }

    private static int getBlockBreakingProgress() {
        return currentBreakingProgress > 0.0F ? (int) (currentBreakingProgress * 10.0F) : -1;
    }

    private static boolean attackBlock(BlockPos pos, Direction direction, @Nullable Runnable beforeBreaking, @Nullable Runnable afterBreaking) {
        if (player.isBlockBreakingRestricted(world, pos, gameMode)) {
            return false;
        } else if (!world.getWorldBorder().contains(pos)) {
            return false;
        } else {
            if (gameMode.isCreative()) {
                breakingBlock = true;
                sendSequencedPacket((sequence) -> {
                    interactionManager.breakBlock(pos);
                    return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                }, beforeBreaking, afterBreaking);
                breakingBlock = false;
            } else if (!breakingBlock || !isCurrentlyBreaking(pos)) {
                if (breakingBlock) {
                    networkHandler.sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, currentBreakingPos, direction));
                    breakingBlock = false;
                }
                BlockState blockState = world.getBlockState(pos);
                var calcBlockBreakingDelta = blockState.calcBlockBreakingDelta(player, player.getEntityWorld(), pos);
                if (calcBlockBreakingDelta >= BREAKING_PROGRESS_MAX) {
                    breakingBlock = true;
                    sendSequencedPacket((sequence) -> {
                        if (!blockState.isAir()) {
                            interactionManager.breakBlock(pos);
                        }
                        return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                    }, beforeBreaking, afterBreaking);
                    breakingBlock = false;
                } else {
                    sendSequencedPacket((sequence) -> {
                        if (!blockState.isAir() && currentBreakingProgress == 0.0F) {
                            blockState.onBlockBreakStart(world, pos, player);
                        }
                        breakingBlock = true;
                        currentBreakingPos = pos;
                        selectedStack = player.getMainHandStack();
                        currentBreakingProgress = 0.0F;
                        world.setBlockBreakingInfo(player.getId(), currentBreakingPos, getBlockBreakingProgress());
                        return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                    });
                }
            }
            ++breakingTickMax;
            return true;
        }
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos, Direction direction, @Nullable Runnable beforeBreaking, @Nullable Runnable afterBreaking) {
        syncSelectedSlot();
        if (gameMode.isCreative() && world.getWorldBorder().contains(pos)) {
            breakingBlock = true;
            sendSequencedPacket((sequence) -> {
                interactionManager.breakBlock(pos);
                return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
            }, beforeBreaking, afterBreaking);
            breakingBlock = false;
            ++breakingTickMax;
            return true;
        } else if (breakingBlock && isCurrentlyBreaking(pos)) {
            BlockState blockState = world.getBlockState(pos);
            if (blockState.isAir()) {
                breakingBlock = false;
                return false;
            } else {
                breakingBlock = true;
                currentBreakingProgress += blockState.calcBlockBreakingDelta(player, player.getEntityWorld(), pos);
                if (currentBreakingProgress >= BREAKING_PROGRESS_MAX) {
                    sendSequencedPacket((sequence) -> {
                        interactionManager.breakBlock(pos);
                        return new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                    }, beforeBreaking, afterBreaking);
                    currentBreakingProgress = 0.0F;
                    breakingBlock = false;
                }
                world.setBlockBreakingInfo(player.getId(), currentBreakingPos, getBlockBreakingProgress());
                ++breakingTickMax;
                return true;
            }
        } else {
            return attackBlock(pos, direction, beforeBreaking, afterBreaking);
        }
    }

    public static void updateBlockBreakingProgress(BlockPos pos) {
        updateBlockBreakingProgress(pos, InteractionUtils.getClosestFace(pos), null, null);
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator, @Nullable Runnable beforeSending, @Nullable Runnable afterSending) {
        try (PendingUpdateManager pendingUpdateManager = world.getPendingUpdateManager().incrementSequence()) {
            int i = pendingUpdateManager.getSequence();
            Packet<ServerPlayPacketListener> packet = packetCreator.predict(i);
            if (beforeSending != null) {
                beforeSending.run();
            }
            networkHandler.sendPacket(packet);
            if (afterSending != null) {
                afterSending.run();
            }
        }
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        sendSequencedPacket(packetCreator, null, null);
    }

    public static void resetBreaking() {
        breakingTicks = 0;
        breakingTickMax = 200;
        breakingBlock = false;
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
}
