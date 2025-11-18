package com.github.bunnyi116.bedrockminer.util.player;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

public class PlayerInventoryUtils {
    public static DefaultedList<ItemStack> getMainStacks(PlayerInventory playerInventory) {
        //#if MC > 12104
        return playerInventory.getMainStacks();
        //#else
        //$$ return playerInventory.main;
        //#endif
    }

    public static int getSelectedSlot() {
        //#if MC > 12104
        return player.getInventory().getSelectedSlot();
        //#else
        //$$ return player.getInventory().selectedSlot;
        //#endif
    }

    public static void setSelectedSlot(int slot) {
        //#if MC > 12104
        player.getInventory().setSelectedSlot(slot);
        //#else
        //$$ player.getInventory().selectedSlot = slot;
        //#endif
    }

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
            if (PlayerInventoryUtils.isItemDamageWarning(itemStack, 5)) {
                continue;
            }
            // 选取最快工具
            float blockBreakingTotalTime = PlayerUtils.getBlockBreakingSpeed(blockState, itemStack);
            if (lastTime == -1 || lastTime < blockBreakingTotalTime) {
                lastTime = blockBreakingTotalTime;
                lastSlot = i;
            }
        }
        if (lastSlot != -1) {
            PlayerInventoryUtils.switchToSlot(lastSlot);
        }
    }

    public static void swapSlots(ClientPlayerEntity player, ClientPlayerInteractionManager interactionManager, int sourceSlot, int hotbarIndex) {
        if (player == null || interactionManager == null) {
            return;
        }
        if (player.currentScreenHandler == player.playerScreenHandler) {
            if (sourceSlot == hotbarIndex) {   // 同一个槽位
                return;
            }
            interactionManager.clickSlot(
                    player.currentScreenHandler.syncId,  // 当前容器ID
                    sourceSlot,                          // 源槽编号
                    hotbarIndex,                         // 目标快捷栏编号（0–8）
                    SlotActionType.SWAP,                 // 交换动作
                    player
            );
        }
    }

    public static void pickFromInventory(ClientPlayerEntity player, ClientPlayerInteractionManager interactionManager, int slot) {
        if (player == null || interactionManager == null) {
            return;
        }

        final var playerInventory = player.getInventory();
        if (player.currentScreenHandler != player.playerScreenHandler) {
            return; // 确保当前不是打开箱子/界面时调用
        }

        // 如果点的是快捷栏内槽位（0–8），直接切换选中即可
        if (PlayerInventory.isValidHotbarIndex(slot)) {
            PlayerInventoryUtils.setSelectedSlot(slot);
            return;
        }

        for (int i = 0; i < PlayerInventory.HOTBAR_SIZE; i++) {
            final var itemStack = playerInventory.getStack(i);
            if (itemStack.isEmpty()) {
                swapSlots(player, interactionManager, slot, i);
                PlayerInventoryUtils.setSelectedSlot(i);
                return;
            }
        }
        swapSlots(player, interactionManager, slot, 0);
        PlayerInventoryUtils.setSelectedSlot(0);
    }

    public static void switchToSlot(int slot) {
        // 背包中没有指定的物品
        if (PlayerInventory.isValidHotbarIndex(slot)) {
            PlayerInventoryUtils.setSelectedSlot(slot);
        } else {
            pickFromInventory(player, interactionManager, slot);
        }
        networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(PlayerInventoryUtils.getSelectedSlot())); // 发送更新手持物品的数据包
    }

    public static void switchToItem(int minDamage, Item... items) {
        final DefaultedList<ItemStack> MainStacks = PlayerInventoryUtils.getMainStacks(playerInventory);
        for (int i = 0; i < MainStacks.size(); i++) {
            ItemStack stack = MainStacks.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            for (Item item : items) {
                if (stack.isOf(item)) {
                    // 检查耐久是否发起警告(剩余耐久<=检查值)
                    if (minDamage > 0 && PlayerInventoryUtils.isItemDamageWarning(stack, minDamage)) {
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
        for (ItemStack stack : PlayerInventoryUtils.getMainStacks(playerInventory)) {
            if (stack.isEmpty()) continue;
            if (PlayerUtils.canInstantlyMineBlock(Blocks.PISTON.getDefaultState(), stack)
                    && !PlayerInventoryUtils.isItemDamageWarning(stack, 5)) {
                return true;
            }
        }
        return false;
    }

    /*** 获取背包物品数量 ***/
    public static int getInventoryItemCount(Item item) {
        return playerInventory.count(item);
    }
}
