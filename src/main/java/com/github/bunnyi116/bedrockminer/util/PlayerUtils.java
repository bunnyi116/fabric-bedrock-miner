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

    public static boolean isWithinBlockInteractionRange(BlockPos blockPos, double additionalRange) {
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

    public static double getBlockInteractionRange() {
        //#if MC>=12005
        if (Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.blockInteractionRange();
        }
        //#else
        //$$ if (gameMode != null) {
        //$$    return gameMode.getPickRange();
        //$$ }
        //#endif
        return 4.5F;
    }
}
