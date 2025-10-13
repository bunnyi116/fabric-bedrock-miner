package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.InventoryManagerUtils;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.PlayerLookManager;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;
import static com.github.bunnyi116.bedrockminer.I18n.*;
import static com.github.bunnyi116.bedrockminer.util.InteractionUtils.getClosestFace;
import static com.github.bunnyi116.bedrockminer.util.InteractionUtils.isBlockWithinReach;

public class TaskManager {
    private static final ArrayList<Task> tasks = new ArrayList<>();
    private static @Nullable Task task = null;
    private static int lastTaskTick = 0;
    private static final int lastTaskTickMax = 40;
    private static boolean working = false;

    public static boolean isProcessing() {
        return processing;
    }

    private static boolean processing;

    public static void tick() {
        if (isDisabled() || !isWorking()) {
            PlayerLookManager.onTick();
            return;
        }
        // 没有任务
        if (tasks.isEmpty() || !isAllowExecutionEnvironment(task != null)) {
            return;
        }
        // 上次任务是否还在队列，如果在不在队列，移除该任务
        if (task != null && !tasks.contains(task)) {
            task = null;
        }
        // 上次任务还存在，检查本次玩家位置是否还符合任务执行条件，如果不符合则重新选择一个新的任务
        if (task != null) {
            // 检查玩家是否在玩家处理范围内
            if (task.world == world && isBlockWithinReach(task.pos, 1F)) {
                processing = true;
                task.tick();
                processing = false;
                if (task.isComplete()) {    // 任务执行后, 如果任务已经完成, 先删除任务减少TICK浪费
                    tasks.remove(task);
                    task = null;
                } else {
                    return; // 任务未完成, 直接返回, 等待下一TICK处理
                }
            }
            // 玩家不在处理范围内 (冷却机制, 如果玩家在规定时间内回到处理位置, 则继续处理, 否则重置)
            else if (tasks.size() > 1 && lastTaskTick++ >= lastTaskTickMax) {
                task = null;
                lastTaskTick = 0;
            }
        }
        // 使用迭代器, 安全删除列表
        var iterator = tasks.iterator();
        while (iterator.hasNext()) {
            var handler = iterator.next();
            // 检查是否有其他任务正在修改视角且不是那个任务
            if (PlayerLookManager.isModify() && PlayerLookManager.getTask() != handler) {
                continue;
            }
            // 检查与目标方块距离
            if (!isBlockWithinReach(handler.pos, getClosestFace(handler.pos), -1)) {
                continue;
            }
            // 玩家切换世界,距离目标方块太远时,删除缓存任务
            if (handler.world != world) {
                iterator.remove();
                continue;
            }
            task = handler;
            return;
        }
    }

    public static void clearTask() {
        tasks.clear();
        if (PlayerLookManager.isModify()) { // 如果有任务正在修改事件, 则还原玩家视角
            PlayerLookManager.reset();
        }
        MessageUtils.addMessage(COMMAND_TASK_CLEAR);
    }

    public static void addTask(Block block, BlockPos pos, ClientWorld world) {
        if (isDisabled() || !isWorking())
            return;
        if (!isAllowExecutionEnvironment(true))
            return;
        if (gameMode.isSurvivalLike()) {    // 仅生存模式添加
            if (!isAllowBlock(block)) {
                return;
            }
            var task = new Task(world, block, pos);
            var config = Config.INSTANCE;
            if (config.floorsBlacklist != null && !config.floorsBlacklist.isEmpty()) {  // 楼层限制
                if (config.floorsBlacklist.contains(pos.getY())) {
                    var msg = FLOOR_BLACK_LIST_WARN.getString().replace("(#floor#)", String.valueOf(pos.getY()));
                    MessageUtils.setOverlayMessage(Text.literal(msg));
                    return;
                }
            }
            for (var targetBlock : tasks) {
                if (targetBlock.pos.equals(pos)) {
                    return;
                }
            }
            tasks.add(task);
        }
    }

    public static void switchOnOff(Block block) {
        if (isDisabled() || !isAllowBlock(block))
            return;
        if (isWorking()) {
            clearTask();
            setWorking(false);
        } else {
            if (gameMode.isCreative()) { // 仅生存模式开启
                MessageUtils.addMessage(FAIL_MISSING_SURVIVAL);
                return;
            }
            setWorking(true);
            if (!client.isInSingleplayer()) {   // 服务器开启警告
                MessageUtils.addMessage(WARN_MULTIPLAYER);
            }
        }
    }


    public static boolean isAllowBlock(Block block) {
        var minecraftClient = MinecraftClient.getInstance();
        var config = Config.INSTANCE;
        // 方块黑名单检查(服务器)
        if (!minecraftClient.isInSingleplayer()) {
            for (var defaultBlockBlack : config.blockBlacklistServer) {
                if (BlockUtils.getBlockId(block).equals(defaultBlockBlack)) {
                    return false;
                }
            }
        }
        // 方块白名单检查(用户自定义)
        for (var blockBlack : config.blockWhitelist) {
            if (BlockUtils.getBlockId(block).equals(blockBlack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAllowExecutionEnvironment(boolean setOverlayMessage) {
        var msg = (Text) null;
        if (gameMode.isCreative()) {
            msg = FAIL_MISSING_SURVIVAL;
        }
        if (client.interactionManager != null && !client.interactionManager.getCurrentGameMode().isSurvivalLike()) {
            msg = FAIL_MISSING_SURVIVAL;
        }
        if (InventoryManagerUtils.getInventoryItemCount(Items.PISTON) < 2) {
            msg = FAIL_MISSING_PISTON;
        }
        if (InventoryManagerUtils.getInventoryItemCount(Items.REDSTONE_TORCH) < 1) {
            msg = FAIL_MISSING_REDSTONETORCH;
        }
        if (InventoryManagerUtils.getInventoryItemCount(Items.SLIME_BLOCK) < 1) {
            msg = FAIL_MISSING_SLIME;
        }
        if (!InventoryManagerUtils.canInstantlyMinePiston()) {
            msg = FAIL_MISSING_INSTANTMINE;
        }
        if (msg != null && setOverlayMessage) {
            MessageUtils.setOverlayMessage(msg);
            return false;
        }
        return true;
    }

    public static boolean isWorking() {
        return working;
    }

    public static void setWorking(boolean working) {
        if (working) {
            MessageUtils.addMessage(TOGGLE_ON);
        } else {
            MessageUtils.addMessage(TOGGLE_OFF);
        }
        TaskManager.working = working;
    }

    private static boolean isDisabled() {
        return Config.INSTANCE.disable;
    }
}
