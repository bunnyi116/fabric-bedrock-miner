package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

public class InventoryUtils {

    public static NonNullList<ItemStack> getMainStacks(Inventory playerInventory) {
        //#if MC > 12104
        return playerInventory.getNonEquipmentItems();
        //#else
        //$$ return playerInventory.items;
        //#endif
    }

    public static int getSelectedSlot() {
        //#if MC > 12104
        return player.getInventory().getSelectedSlot();
        //#else
        //$$ return player.getInventory().selected;
        //#endif
    }

    public static void setSelectedSlot(int slot) {
        //#if MC > 12104
        player.getInventory().setSelectedSlot(slot);
        //#else
        //$$ player.getInventory().selected = slot;
        //#endif
    }

    public static void autoSwitch(Block block) {
        autoSwitch(block.defaultBlockState());
    }

    public static void autoSwitch(BlockState blockState) {
        // 选取最优工具
        float lastTime = -1;
        int lastSlot = -1;
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack itemStack = playerInventory.getItem(i);
            // 检查耐久是否发起警告(剩余耐久<=检查值)
            if (InventoryUtils.isItemDamageWarning(itemStack, 5)) {
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
            InventoryUtils.switchToSlot(lastSlot);
        }
    }

    public static void swapSlots(LocalPlayer player, MultiPlayerGameMode interactionManager, int sourceSlot, int hotbarIndex) {
        if (player == null || interactionManager == null) {
            return;
        }
        if (player.containerMenu == player.inventoryMenu) {
            if (sourceSlot == hotbarIndex) {   // 同一个槽位
                return;
            }
            interactionManager.handleContainerInput(
                    player.containerMenu.containerId,   // 当前容器ID
                    sourceSlot,                          // 源槽编号
                    hotbarIndex,                         // 目标快捷栏编号（0–8）
                    ContainerInput.SWAP,                     // 交换动作
                    player
            );
        }
    }

    public static void pickFromInventory(LocalPlayer player, MultiPlayerGameMode interactionManager, int slot) {
        if (player == null || interactionManager == null) {
            return;
        }

        Inventory playerInventory = player.getInventory();
        // 容器菜单（containerMenu）背包菜单（playerScreenHandler)
        if (player.containerMenu != player.inventoryMenu) {
            return; // 确保当前不是打开箱子/界面时调用
        }

        // 如果点的是快捷栏内槽位（0–8），直接切换选中即可
        if (Inventory.isHotbarSlot(slot)) {
            InventoryUtils.setSelectedSlot(slot);
            return;
        }

        for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
            final ItemStack itemStack = playerInventory.getItem(i);
            if (itemStack.isEmpty()) {
                swapSlots(player, interactionManager, slot, i);
                InventoryUtils.setSelectedSlot(i);
                return;
            }
        }
        swapSlots(player, interactionManager, slot, 6);
        InventoryUtils.setSelectedSlot(6);
    }

    public static void switchToSlot(int slot) {
        // 背包中没有指定的物品
        if (Inventory.isHotbarSlot(slot)) {
            InventoryUtils.setSelectedSlot(slot);
        } else {
            pickFromInventory(player, interactionManager, slot);
        }
        NetworkUtils.sendPacket(new ServerboundSetCarriedItemPacket(InventoryUtils.getSelectedSlot())); // 发送更新手持物品的数据包
    }

    public static void switchToItem(int minDamage, Item... items) {
        NonNullList<ItemStack> MainStacks = InventoryUtils.getMainStacks(playerInventory);
        for (int i = 0; i < MainStacks.size(); i++) {
            ItemStack stack = MainStacks.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            for (Item item : items) {
                if (stack.is(item)) {
                    // 检查耐久是否发起警告(剩余耐久<=检查值)
                    if (minDamage > 0 && InventoryUtils.isItemDamageWarning(stack, minDamage)) {
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
            int damage = itemStack.getDamageValue();     // 获取物品的已使用耐久度
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
        for (ItemStack stack : InventoryUtils.getMainStacks(playerInventory)) {
            if (stack.isEmpty()) continue;
            if (PlayerUtils.canInstantlyMineBlock(Blocks.PISTON.defaultBlockState(), stack)
                    && !InventoryUtils.isItemDamageWarning(stack, 5)) {
                return true;
            }
        }
        return false;
    }

    /*** 获取背包物品数量 ***/
    public static int getInventoryItemCount(Item item) {
        return playerInventory.countItem(item);
    }
}