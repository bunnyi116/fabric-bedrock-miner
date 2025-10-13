package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.FluidTags;

import java.util.Objects;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;


public class InventoryManagerUtils {

    public static void autoSwitch(Block block) {
        autoSwitch(block.getDefaultState());
    }

    public static void autoSwitch(BlockState blockState) {
        // 选取最优工具
        float lastTime = -1;
        int lastSlot = -1;
        for (int i = 0; i < playerInventory.size(); i++) {
            var itemStack = playerInventory.getStack(i);
            // 检查耐久是否发起警告(剩余耐久<=检查值)
            if (InventoryManagerUtils.isItemDamageWarning(itemStack, 5)) {
                continue;
            }
            // 选取最快工具
            float blockBreakingTotalTime = InventoryManagerUtils.getBlockBreakingTotalTime(blockState, itemStack);
            if (blockBreakingTotalTime != -1) {
                if (lastTime == -1 || lastTime > blockBreakingTotalTime) {
                    lastTime = blockBreakingTotalTime;
                    lastSlot = i;
                }
            }
        }
        if (lastSlot != -1) {
            InventoryManagerUtils.switchToSlot(lastSlot);
        }
    }

    public static void switchToSlot(int slot) {
        // 背包中没有指定的物品
        if (PlayerInventory.isValidHotbarIndex(slot)) {
            playerInventory.setSelectedSlot(slot);
        }
//        else {
//            interactionManager.pickItemFromEntity(player, true);
//        }
        networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(playerInventory.getSelectedSlot())); // 发送更新手持物品的数据包
    }

    public static void switchToItem(int minDamage, Item... items) {
        // 遍历主背包
        for (int i = 0; i < playerInventory.getMainStacks().size(); i++) {
            ItemStack stack = playerInventory.getMainStacks().get(i);
            if (stack.isEmpty()) {
                continue;
            }
            for (Item item : items) {
                if (stack.isOf(item)) {
                    // 检查耐久是否发起警告(剩余耐久<=检查值)
                    if (minDamage > 0 && InventoryManagerUtils.isItemDamageWarning(stack, minDamage)) {
                        continue;
                    }
                    switchToSlot(i);
                    return;
                }
            }
        }
    }


    public static void switchToItem(Item... items) {
        switchToItem(-1, items);
    }

    /**
     * 判断物品的耐久度是否小于等于给定的最小值
     *
     * @param itemStack 要检查耐久度的物品堆栈
     * @param minDamage 最小的剩余耐久度
     * @return 如果物品的剩余耐久度小于等于最小值则返回true，否则返回false
     */
    public static boolean isItemDamageWarning(ItemStack itemStack, int minDamage) {
        int damageMax = itemStack.getMaxDamage();   // 获取物品的最大耐久度
        if (damageMax > 0) {
            int damage = itemStack.getDamage();     // 获取物品的已使用耐久度
            int damageSurplus = damageMax - damage; // 计算物品的剩余耐久度
            return damageSurplus <= minDamage;      // 如果剩余耐久度小于等于给定的最小值，则返回true
        }
        return false;   // 如果物品没有耐久度，则返回false
    }

    /**
     * 判断玩家是否可以瞬间破坏活塞方块
     *
     * @return 如果可以瞬间破坏活塞方块则返回true，否则返回false
     */
    public static boolean canInstantlyMinePiston() {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null) return false;
        var playerInventory = player.getInventory();
        for (int i = 0; i < playerInventory.size(); i++) {
            if (isInstantBreakingBlock(Blocks.PISTON.getDefaultState(), playerInventory.getStack(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否可以瞬间破坏方块
     *
     * @param blockState 要破坏的方块状态
     * @param itemStack  使用工具/物品破坏方块
     * @return true为可以瞬间破坏
     */
    public static boolean isInstantBreakingBlock(BlockState blockState, ItemStack itemStack) {
        float hardness = blockState.getBlock().getHardness();       // 当前方块硬度
        if (hardness < 0) return false;                             // 无硬度(如基岩无法破坏)
        float speed = getBlockBreakingSpeed(blockState, itemStack); // 当前破坏速度
        return speed > (hardness * 30);
    }

    /**
     * 获取方块破坏所需的总时间
     *
     * @param blockState 要破坏的方块状态
     * @param itemStack  使用工具/物品破坏方块
     * @return 当前物品破坏该方块所需的时间 (单位为秒)
     */
    public static float getBlockBreakingTotalTime(BlockState blockState, ItemStack itemStack) {
        var hardness = blockState.getBlock().getHardness();         // 当前方块硬度
        if (hardness < 0) return -1;
        var speed = getBlockBreakingSpeed(blockState, itemStack);   // 当前工具的破坏速度系数
        return (float) ((hardness * 1.5) / speed);
    }

    /**
     * 获取当前物品能够破坏指定方块的破坏速度.
     *
     * @param blockState 要破坏的方块状态
     * @param itemStack  使用工具/物品破坏方块
     * @return 当前物品破坏该方块所需的时间（单位为 tick）
     */
    private static float getBlockBreakingSpeed(BlockState blockState, ItemStack itemStack) {
        var f = itemStack.getMiningSpeedMultiplier(blockState);  // 当前物品的破坏系数速度
        // 根据工具的"效率"附魔增加破坏速度
        if (f > 1.0F) {
            // 获取itemStack的附魔集合
            for (var enchantment : itemStack.getEnchantments().getEnchantments()) {
                var enchantmentKey = enchantment.getKey();
                if (enchantmentKey.isPresent()) {
                    // 获取效率附魔等级
                    if (enchantmentKey.get() == Enchantments.EFFICIENCY) {
                        int toolLevel = EnchantmentHelper.getLevel(enchantment, itemStack);
                        if (toolLevel > 0 && !itemStack.isEmpty()) {
                            f += (float) (toolLevel * toolLevel + 1);
                        }
                    }
                }
            }
        }
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
        f *= (float) player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
        if (player.isSubmergedIn(FluidTags.WATER)) {
            var submergedMiningSpeed = player.getAttributeInstance(EntityAttributes.SUBMERGED_MINING_SPEED);
            if (submergedMiningSpeed != null) {
                f *= (float) submergedMiningSpeed.getValue();
            }
        }
        if (!player.isOnGround()) { // 如果玩家不在地面上，则减缓破坏速度
            f /= 5.0F;
        }
        // 如果玩家不在地面上，则减缓破坏速度
        if (!player.isOnGround()) {
            f /= 5.0F;
        }
        return f;
    }

    /*** 获取背包物品数量 ***/
    public static int getInventoryItemCount(Item item) {
        return playerInventory.count(item);
    }
}
