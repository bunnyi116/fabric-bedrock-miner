package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

public class PlayerUtils {
    public static double getHorizontalDistanceToPlayer(BlockPos pos) {
        double dx = pos.getX() - player.getX();
        double dz = pos.getZ() - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * 获取最近的面
     */
    public static Direction getClosestFace(BlockPos targetPos) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 targetCenterPos = Vec3.atCenterOf(targetPos);
        Direction closestFace = null;
        double closestDistanceSquared = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            double offsetX = direction.getStepX() * 0.5;
            double offsetY = direction.getStepY() * 0.5;
            double offsetZ = direction.getStepZ() * 0.5;
            Vec3 facePos = targetCenterPos.add(offsetX, offsetY, offsetZ);
            double distanceSquared = playerPos.distanceToSqr(facePos);
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closestFace = direction;
            }
        }
        return closestFace;
    }

    /**
     * 判断玩家是否位于可与指定方块交互的距离范围内。
     * 用于检测破坏、放置或右键交互是否有效。
     */
    public static boolean canInteractWithBlockAt(BlockPos blockPos, double additionalRange) {
        double blockPosX = blockPos.getX();
        double blockPosY = blockPos.getY();
        double blockPosZ = blockPos.getZ();
        //#if MC >= 12005
        double distance = getBlockInteractionRange() + additionalRange;
        double eyePosX = player.getX();
        double eyePosY = player.getEyeY();
        double eyePosZ = player.getZ();
        double dx = Math.max(Math.max(blockPosX - eyePosX, eyePosX - (blockPosX + 1)), 0);
        double dy = Math.max(Math.max(blockPosY - eyePosY, eyePosY - (blockPosY + 1)), 0);
        double dz = Math.max(Math.max(blockPosZ - eyePosZ, eyePosZ - (blockPosZ + 1)), 0);
        return (dx * dx + dy * dy + dz * dz) < (distance * distance);
        //#elseif MC >= 11900
        //$$ double distance = Math.max(getBlockInteractionRange(), 5) + additionalRange;
        //$$ double eyePosX = player.getX();
        //$$ double eyePosY = player.getEyeY();
        //$$ double eyePosZ = player.getZ();
        //$$ double dx = eyePosX - (blockPosX + 0.5);
        //$$ double dy = eyePosY - (blockPosY + 0.5);
        //$$ double dz = eyePosZ - (blockPosZ + 0.5);
        //$$ return (dx * dx + dy * dy + dz * dz) < (distance * distance);
        //#else
        //$$ // MC <= 1.18.2
        //$$ double distance = Math.max(getBlockInteractionRange(), 5) + additionalRange;
        //$$ double dx = player.getX() - (blockPosX + 0.5);
        //$$ double dy = player.getY() - (blockPosY + 0.5) + 1.5;
        //$$ double dz = player.getZ() - (blockPosZ + 0.5);
        //$$ return (dx * dx + dy * dy + dz * dz) < (distance * distance);
        //#endif
    }

    /**
     * 获取方块交互范围
     */
    public static double getBlockInteractionRange() {
        //#if MC>=12005
        if (Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.blockInteractionRange();
        }
        //#else
        //$$ if (interactionManager != null) {
        //$$    return interactionManager.getPickRange();
        //$$ }
        //#endif
        return 4.5F;
    }

    /**
     * 计算当前TICK方块破坏增量
     */
    public static float calcBlockBreakingDelta(BlockState state, ItemStack itemStack) {
        float hardness = state.getBlock().defaultDestroyTime();
        if (hardness == -1.0F) {
            return 0.0F;
        } else {
            int i = player.hasCorrectToolForDrops(state) ? 30 : 100;
            return getBlockBreakingSpeed(state, itemStack) / hardness / (float) i;
        }
    }

    public static float calcBlockBreakingDelta(BlockState state) {
        return calcBlockBreakingDelta(state, player.getMainHandItem());
    }

    public static boolean canInstantlyMineBlock(BlockState state, ItemStack itemStack) {
        return PlayerUtils.calcBlockBreakingDelta(state, itemStack) >= InteractionUtils.BREAKING_PROGRESS_MAX;
    }

    public static boolean canInstantlyMineBlock(BlockState state) {
        return canInstantlyMineBlock(state, player.getMainHandItem());
    }

    /**
     * 获取当前物品能够破坏指定方块的破坏速度.
     *
     * @param blockState 要破坏的方块状态
     * @param itemStack  使用工具/物品破坏方块
     * @return 当前物品破坏该方块所需的时间（单位为 tick）
     */
    public static float getBlockBreakingSpeed(BlockState blockState, ItemStack itemStack) {
        var f = itemStack.getDestroySpeed(blockState);  // 当前物品的破坏系数速度


        // 根据工具的"效率"附魔增加破坏速度
        //#if MC > 12006
        if (f > 1.0F) {
            for (var enchantment : itemStack.getEnchantments().keySet()) {
                var enchantmentKey = enchantment.unwrapKey();
                if (enchantmentKey.isPresent()) {
                    if (enchantmentKey.get() == Enchantments.EFFICIENCY) {
                        int level = EnchantmentHelper.getItemEnchantmentLevel(enchantment, itemStack);
                        if (level > 0 && !itemStack.isEmpty()) {
                            f += (float) (level * level + 1);
                        }
                    }
                }
            }
        }
        //#else
        //$$ if (f > 1.0F) {
        //$$     int level = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.EFFICIENCY, itemStack);
        //$$     if (level > 0 && !itemStack.isEmpty()) {
        //$$         f += (float)(level * level + 1);
        //$$     }
        //$$ }
        //#endif

        // 根据玩家"急迫"状态效果增加破坏速度
        if (MobEffectUtil.hasDigSpeed(player)) {
            f *= 1.0F + (float)(MobEffectUtil.getDigSpeedAmplification(player) + 1) * 0.2F;
        }

        // 根据玩家"挖掘疲劳"状态效果减缓破坏速度
        if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
            float g = switch (Objects.requireNonNull(player.getEffect(MobEffects.MINING_FATIGUE)).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
            f *= g;
        }

        // 如果玩家在水中并且没有"水下速掘"附魔，则减缓破坏速度
        //#if MC > 12006
        f *= (float) player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (player.isEyeInFluid(FluidTags.WATER)) {
            var submergedMiningSpeed = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
            if (submergedMiningSpeed != null) {
                f *= (float) submergedMiningSpeed.getValue();
            }
        }
        //#else
        //$$ if (player.isEyeInFluid(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)) {
        //$$     f /= 5.0F;
        //$$ }
        //#endif

        // 如果玩家不在地面上，则减缓破坏速度
        if (!player.onGround()) {
            f /= 5.0F;
        }
        return f;
    }

}
