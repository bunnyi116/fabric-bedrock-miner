package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.level;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;

public class BlockUtils {
    public static @NotNull Block getBlock(Identifier blockId) {
        //#if MC > 12101
        return BuiltInRegistries.BLOCK.getValue(blockId);
        //#else
        //$$ return BuiltInRegistries.BLOCK.get(blockId);
        //#endif
    }

    public static String getBlockName(Block block) {
        return block.getName().getString();
    }

    public static Identifier getKey(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    public static String getKeyString(Block block) {
        return getKey(block).toString();
    }

    public static boolean canSupportCenter(BlockPos blockPos, Direction direction) {
        return Block.canSupportCenter(level, blockPos, direction);
    }

    public static boolean canPlace(BlockPos placePos, BlockState stateToPlace) {
        return canPlace(placePos, stateToPlace, false);
    }

    public static boolean canPlace(BlockPos placePos, BlockState stateToPlace, boolean checkSurvive) {
        if (!BlockUtils.isReplaceable(level.getBlockState(placePos))) {
            //TODO: 因为破基岩没有台阶(Slabs)需求, 所以没进一步对双层方块检查
            return false;
        }
        if (checkSurvive && !stateToPlace.canSurvive(level, placePos)) {
            return false;
        }
        CollisionContext collisionContext = player != null
                ? CollisionContext.placementContext(player)
                : CollisionContext.empty();
        return level.isUnobstructed(stateToPlace, placePos, collisionContext);
    }


    public static boolean isReplaceable(BlockState blockState) {
        //#if MC > 11902
        return blockState.canBeReplaced();
        //#else
        //$$ return blockState.getMaterial().isReplaceable();
        //#endif
    }

    public static boolean canInstantlyMineBlock(BlockState state) {
        return canInstantlyMineBlock(state, player.getMainHandItem());
    }

    public static boolean canInstantlyMineBlock(BlockState state, ItemStack itemStack) {
        return getDestroyProgress(state, itemStack) >= 0.7F;
    }

    public static float getDestroyProgress(BlockState state) {
        return getDestroyProgress(state, player.getMainHandItem());
    }

    public static float getDestroyProgress(BlockState state, ItemStack itemStack) {
        float hardness = state.getBlock().defaultDestroyTime();
        if (hardness == -1.0F) {
            return 0.0F;
        } else {
            int i = player.hasCorrectToolForDrops(state) ? 30 : 100;
            return getDestroySpeed(state, itemStack) / hardness / (float) i;
        }
    }

    public static float getDestroySpeed(BlockState blockState, ItemStack itemStack) {
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
