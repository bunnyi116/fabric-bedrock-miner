package com.github.bunnyi116.bedrockminer.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

@Environment(EnvType.CLIENT)
public class ClientPlayerInteractionManagerUtils {
    private static final float BREAKING_PROGRESS_MAX = 0.7F;

    private static BlockPos currentBreakingPos = new BlockPos(-1, -1, -1);
    private static ItemStack selectedStack;
    private static float currentBreakingProgress;
    private static boolean breakingBlock;
    private static int lastSelectedSlot;
    private static int breakingTicks;
    private static int breakingTickMax;

    private static void syncSelectedSlot() {
        int selectedSlot = player.getInventory().selectedSlot;
        if (selectedSlot != lastSelectedSlot) {
            lastSelectedSlot = selectedSlot;
            sendPacket(new UpdateSelectedSlotC2SPacket(lastSelectedSlot));
        }
    }

    private static boolean isCurrentlyBreaking(BlockPos pos) {
//        ItemStack itemStack = player.getMainHandStack();
//        return pos.equals(currentBreakingPos) && ItemStack.areItemsAndComponentsEqual(itemStack, selectedStack);
        return pos.equals(currentBreakingPos);
    }

    private static int getBlockBreakingProgress() {
        var breakingProgress = currentBreakingProgress >= BREAKING_PROGRESS_MAX ? 1.0F : currentBreakingProgress;
        return breakingProgress > 0.0F ? (int) (breakingProgress * 10.0F) : -1;
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos, Direction direction, @Nullable Runnable beforeBreaking, @Nullable Runnable afterBreaking) {
        syncSelectedSlot();
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        } else if (player.isBlockBreakingRestricted(world, pos, gameMode)) {
            return false;
        } else if (!player.canInteractWithBlockAt(pos, 1.0F)) {
            return false;
        } else if (gameMode.isCreative()) {    // 创造模式下
            breakingBlock = true;
            client.getTutorialManager().onBlockBreaking(world, pos, world.getBlockState(pos), 1.0F);
            sendSequencedPacket((sequence) -> { // 只需要发送START包，因为它是瞬间破坏的
                interactionManager.breakBlock(pos);
                return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
            }, beforeBreaking, afterBreaking);
            breakingBlock = false;
            return true;
        }
        if (breakingBlock && isCurrentlyBreaking(pos)) {
            BlockState blockState = world.getBlockState(pos);
            if (blockState.isAir()) {
                breakingBlock = false;
                return true;
            }
            currentBreakingProgress += blockState.calcBlockBreakingDelta(player, player.getWorld(), pos);
            if (currentBreakingProgress >= BREAKING_PROGRESS_MAX) {
                client.getTutorialManager().onBlockBreaking(world, pos, blockState, 1.0F);
            } else {
                client.getTutorialManager().onBlockBreaking(world, pos, blockState, MathHelper.clamp(currentBreakingProgress, 0.0F, 1.0F));
            }
            if (currentBreakingProgress >= BREAKING_PROGRESS_MAX) {
                sendSequencedPacket((sequence) -> {
                    if (!blockState.isAir()) {
                        interactionManager.breakBlock(pos);
                    }
                    return new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                }, beforeBreaking, afterBreaking);
                breakingBlock = false;
                currentBreakingProgress = 0.0F;
                world.setBlockBreakingInfo(player.getId(), currentBreakingPos, -1);
                return true;
            } else {
                world.setBlockBreakingInfo(player.getId(), currentBreakingPos, getBlockBreakingProgress());
            }

            ++breakingTickMax;
        } else {
            if (breakingBlock && !isCurrentlyBreaking(pos)) {
                sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, currentBreakingPos, direction));
                breakingBlock = false;
            }
            BlockState blockState = world.getBlockState(pos);
            client.getTutorialManager().onBlockBreaking(world, pos, blockState, 0.0F);
            final float calcBlockBreakingDelta = blockState.calcBlockBreakingDelta(player, player.getWorld(), pos);
            if (calcBlockBreakingDelta >= BREAKING_PROGRESS_MAX) {
                breakingBlock = true;
                sendSequencedPacket((sequence) -> {
                    if (!blockState.isAir()) {
                        interactionManager.breakBlock(pos);
                    }
                    return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                }, beforeBreaking, afterBreaking);
                breakingBlock = false;
                return true;
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
        return false;
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos, Direction direction) {
        return updateBlockBreakingProgress(pos, direction, null, null);
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos) {
        return updateBlockBreakingProgress(pos, InteractionUtils.getClosestFace(pos));
    }

    public static void sendPacket(Packet<?> packet, @Nullable Runnable beforeSending, @Nullable Runnable afterSending) {
        if (beforeSending != null) {
            beforeSending.run();
        }
        networkHandler.sendPacket(packet);
        if (afterSending != null) {
            afterSending.run();
        }
    }

    public static void sendPacket(Packet<?> packet) {
        sendPacket(packet, null, null);
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator, @Nullable Runnable beforeSending, @Nullable Runnable afterSending) {
        try (PendingUpdateManager pendingUpdateManager = world.getPendingUpdateManager().incrementSequence()) {
            int i = pendingUpdateManager.getSequence();
            Packet<ServerPlayPacketListener> packet = packetCreator.predict(i);
            sendPacket(packet, beforeSending, afterSending);
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

    public static boolean canPlace(BlockState placeBlockState, BlockPos blockPos) {
        boolean place = world.getBlockState(blockPos).isReplaceable();
        var shape = placeBlockState.getCollisionShape(world, blockPos);
        if (!shape.isEmpty()) {
            for (var entity : world.getEntities()) {
                EntityType<?> entityType = entity.getType();
                // 忽略不应阻碍放置的实体类型
                if (entityType.getSpawnGroup() != SpawnGroup.MISC)
                    continue;
                if (entityType == EntityType.ITEM                           // 物品
                        || entityType == EntityType.BLOCK_DISPLAY           // 方块展示实体
                        || entityType == EntityType.ITEM_DISPLAY            // 物品展示实体
                        || entityType == EntityType.TEXT_DISPLAY            // 文本展示实体
                        || entityType == EntityType.ARROW                   // 箭
                        || entityType == EntityType.AREA_EFFECT_CLOUD       // 区域效果云
                        || entityType == EntityType.DRAGON_FIREBALL         // 末影龙火球
                        || entityType == EntityType.EGG                     // 掷出的鸡蛋
                        || entityType == EntityType.ENDER_PEARL             // 掷出的末影珍珠
                        || entityType == EntityType.EVOKER_FANGS            // 唤魔者尖牙
                        || entityType == EntityType.EXPERIENCE_BOTTLE       // 掷出的附魔之瓶
                        || entityType == EntityType.EXPERIENCE_ORB          // 经验球
                        || entityType == EntityType.EYE_OF_ENDER            // 末影之眼
                        || entityType == EntityType.FIREWORK_ROCKET         // 烟花火箭
                        || entityType == EntityType.INTERACTION             // 交互实体
                        || entityType == EntityType.LEASH_KNOT              // 拴绳结
                        || entityType == EntityType.LIGHTNING_BOLT          // 闪电束
                        || entityType == EntityType.LLAMA_SPIT              // 羊驼唾沫
                        || entityType == EntityType.MARKER                  // 标记
                ) {
                    continue;
                }
                // 检查碰撞体积
                if (entity.collidesWithStateAtPos(blockPos, placeBlockState)) {
                    place = false;
                    break;
                }
            }
        }
        return place;
    }
}
