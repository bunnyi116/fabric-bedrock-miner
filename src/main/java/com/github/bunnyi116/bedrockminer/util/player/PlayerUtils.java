package com.github.bunnyi116.bedrockminer.util.player;

import com.github.bunnyi116.bedrockminer.util.ClientPlayerInteractionManagerUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;

public class PlayerUtils {
    /**
     * 获取最近的面
     */
    public static Direction getClosestFace(BlockPos targetPos) {
        Vec3d playerPos = player.getEyePos();
        Vec3d targetCenterPos = Vec3d.ofCenter(targetPos);
        Direction closestFace = null;
        double closestDistanceSquared = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            double offsetX = direction.getOffsetX() * 0.5;
            double offsetY = direction.getOffsetY() * 0.5;
            double offsetZ = direction.getOffsetZ() * 0.5;
            Vec3d facePos = targetCenterPos.add(offsetX, offsetY, offsetZ);
            double distanceSquared = playerPos.squaredDistanceTo(facePos);
            // 更新最近的面
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
        if (MinecraftClient.getInstance().player != null) {
            return MinecraftClient.getInstance().player.getBlockInteractionRange();
        }
        //#else
        //$$ if (MinecraftClient.getInstance().interactionManager != null) {
        //$$    return MinecraftClient.getInstance().interactionManager.getReachDistance();
        //$$ }
        //#endif
        return 4.5F;
    }

    /**
     * 计算当前TICK方块破坏增量
     */
    public static float calcBlockBreakingDelta(BlockState state, ItemStack itemStack) {
        float hardness = state.getBlock().getHardness();
        if (hardness == -1.0F) {
            return 0.0F;
        } else {
            int i = player.canHarvest(state) ? 30 : 100;
            return getBlockBreakingSpeed(state, itemStack) / hardness / (float) i;
        }
    }

    public static float calcBlockBreakingDelta(BlockState state) {
        return calcBlockBreakingDelta(state, player.getMainHandStack());
    }

    public static boolean canInstantlyMineBlock(BlockState state, ItemStack itemStack) {
        return PlayerUtils.calcBlockBreakingDelta(state, itemStack) >= ClientPlayerInteractionManagerUtils.BREAKING_PROGRESS_MAX;
    }

    public static boolean canInstantlyMineBlock(BlockState state) {
        return canInstantlyMineBlock(state, player.getMainHandStack());
    }

    /**
     * 获取当前物品能够破坏指定方块的破坏速度.
     *
     * @param blockState 要破坏的方块状态
     * @param itemStack  使用工具/物品破坏方块
     * @return 当前物品破坏该方块所需的时间（单位为 tick）
     */
    public static float getBlockBreakingSpeed(BlockState blockState, ItemStack itemStack) {
        var f = itemStack.getMiningSpeedMultiplier(blockState);  // 当前物品的破坏系数速度

        // 根据工具的"效率"附魔增加破坏速度
        //#if MC > 12006
        if (f > 1.0F) {
            for (var enchantment : itemStack.getEnchantments().getEnchantments()) {
                var enchantmentKey = enchantment.getKey();
                if (enchantmentKey.isPresent()) {
                    if (enchantmentKey.get() == Enchantments.EFFICIENCY) {
                        int level = EnchantmentHelper.getLevel(enchantment, itemStack);
                        if (level > 0 && !itemStack.isEmpty()) {
                            f += (float) (level * level + 1);
                        }
                    }
                }
            }
        }
        //#else
        //$$ if (f > 1.0F) {
        //$$     int level = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, itemStack);
        //$$     if (level > 0 && !itemStack.isEmpty()) {
        //$$         f += (float)(level * level + 1);
        //$$     }
        //$$ }
        //#endif

        // 根据玩家"急迫"状态效果增加破坏速度
        if (StatusEffectUtil.hasHaste(player)) {
            f *= 1.0F + (float) (StatusEffectUtil.getHasteAmplifier(player) + 1) * 0.2F;
        }

        // 根据玩家"挖掘疲劳"状态效果减缓破坏速度
        if (player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            float g = switch (Objects.requireNonNull(player.getStatusEffect(StatusEffects.MINING_FATIGUE)).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
            f *= g;
        }

        // 如果玩家在水中并且没有"水下速掘"附魔，则减缓破坏速度
        //#if MC > 12006
        f *= (float) player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
        if (player.isSubmergedIn(FluidTags.WATER)) {
            var submergedMiningSpeed = player.getAttributeInstance(EntityAttributes.SUBMERGED_MINING_SPEED);
            if (submergedMiningSpeed != null) {
                f *= (float) submergedMiningSpeed.getValue();
            }
        }
        //#else
        //$$ if (player.isSubmergedIn(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)) {
        //$$     f /= 5.0F;
        //$$ }
        //#endif

        if (!player.isOnGround()) { // 如果玩家不在地面上，则减缓破坏速度
            f /= 5.0F;
        }
        // 如果玩家不在地面上，则减缓破坏速度
        if (!player.isOnGround()) {
            f /= 5.0F;
        }
        return f;
    }
}
