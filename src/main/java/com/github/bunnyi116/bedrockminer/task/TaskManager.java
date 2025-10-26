package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.InventoryManagerUtils;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.PlayerLookManager;
import net.minecraft.block.Block;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;
import static com.github.bunnyi116.bedrockminer.I18n.*;

public class TaskManager {
    public static TaskManager INSTANCE = new TaskManager();
    private final ArrayList<Task> pendingTasks = new ArrayList<>();
    @Nullable
    private Task currentTask;
    private boolean working;
    private boolean processing;
    private int resetCount;

    public TaskManager() {
        this.currentTask = null;
        this.working = false;
        this.processing = false;
        this.resetCount = 0;
    }

    public boolean isProcessing() {
        return processing;
    }

    public void tick() {
        if (!gameVariableIsValid()) {
            return;
        }
        if (this.isDisabled() || !this.isWorking()) {
            PlayerLookManager.INSTANCE.tick();
            return;
        }
        if (this.pendingTasks.isEmpty() && Config.INSTANCE.ranges.isEmpty()) {
            this.currentTask = null;
            return;
        }
        final var setOverlayMessage = currentTask != null;
        if (isAllowExecutionEnvironment(setOverlayMessage)) {
            if (this.currentTask != null) {
                if (this.currentTask.world == world && this.currentTask.canInteractWithBlockAt()) {
                    this.processing = true;
                    this.currentTask.tick();
                    this.resetCount = 0;
                    this.processing = false;
                    if (this.currentTask.isComplete()) {
                        this.pendingTasks.remove(this.currentTask);
                        this.currentTask = null;
                    } else {
                        return;
                    }
                } else if ((this.pendingTasks.size() > 1 || !Config.INSTANCE.ranges.isEmpty()) && this.resetCount++ >= 40) {
                    this.currentTask = null;
                    this.resetCount = 0;
                }
            }
            if (this.currentTask == null) {
                final var iterator1 = pendingTasks.iterator();
                while (iterator1.hasNext()) {
                    var task = iterator1.next();
                    if (!task.canInteractWithBlockAt()) {
                        continue;
                    }
                    if (PlayerLookManager.INSTANCE.isModify() && PlayerLookManager.INSTANCE.getTask() != task) {
                        continue;
                    }
                    if (task.world != world) {
                        iterator1.remove();
                        continue;
                    }
                    this.currentTask = task;
                    return;
                }
            }

            if (this.currentTask == null) {
                final var iterator2 = Config.INSTANCE.ranges.iterator();
                while (iterator2.hasNext()) {
                    var range = iterator2.next();
                    if (!range.isForWorld(world)) {
                        continue;
                    }
                    var rangeBox =  BlockBox.create(range.pos1, range.pos2);
                    var playerBox = new BlockBox(player.getBlockPos());
                    var playerExpandBox = playerBox.expand((int) player.getBlockInteractionRange());
                    if (rangeBox.intersects(playerExpandBox)) {
                        final var blockInteractionRange = (int) player.getBlockInteractionRange() - 1;
                        for (int y = blockInteractionRange; y > -blockInteractionRange; y--) {
                            for (int x = -blockInteractionRange; x <= blockInteractionRange; x++) {
                                for (int z = -blockInteractionRange; z <= blockInteractionRange; z++) {
                                    final var blockPos = player.getBlockPos().add(x, y, z);
                                    final var box = new BlockBox(blockPos);
                                    if (rangeBox.intersects(box)) {
                                        final var blockState = world.getBlockState(blockPos);
                                        final var block = blockState.getBlock();
                                        if (blockState.isAir() || blockState.isReplaceable()) {
                                            continue;
                                        }
                                        if (!Config.INSTANCE.isAllowBlock(block)) {
                                            continue;
                                        }
                                        if (Config.INSTANCE.isFloorsBlacklist(blockPos)) {
                                            continue;
                                        }
                                        var task = new Task(world, block, blockPos);
                                        if (!task.canInteractWithBlockAt()) {
                                            continue;
                                        }
                                        if (PlayerLookManager.INSTANCE.isModify() && PlayerLookManager.INSTANCE.getTask() != task) {
                                            continue;
                                        }
                                        if (task.world != world) {
                                            iterator2.remove();
                                            continue;
                                        }
                                        this.currentTask = task;
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void clearTask() {
        pendingTasks.clear();
        if (PlayerLookManager.INSTANCE.isModify()) { // 如果有任务正在修改事件, 则还原玩家视角
            PlayerLookManager.INSTANCE.reset();
        }
        MessageUtils.addMessage(COMMAND_TASK_CLEAR);
    }

    public void addTask(Block block, BlockPos pos, ClientWorld world) {
        if (isDisabled() || !isWorking()) {
            return;
        }
        if (!isAllowExecutionEnvironment(true)) {
            return;
        }
        if (!gameMode.isSurvivalLike()) {
            return;
        }
        if (!Config.INSTANCE.isAllowBlock(block)) {
            return;
        }
        if (Config.INSTANCE.isFloorsBlacklist(pos)) {  // 楼层限制
            var msg = FLOOR_BLACK_LIST_WARN.getString().replace("(#floor#)", String.valueOf(pos.getY()));
            MessageUtils.setOverlayMessage(Text.literal(msg));
            return;
        }
        for (var targetBlock : pendingTasks) {
            if (targetBlock.pos.equals(pos)) {
                return;
            }
        }
        pendingTasks.add(new Task(world, block, pos));
    }

    public boolean isAllowExecutionEnvironment(boolean setOverlayMessage) {
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

    public boolean isWorking() {
        return working;
    }

    public void setWorking(boolean working) {
        if (working) {
            MessageUtils.addMessage(TOGGLE_ON);
        } else {
            MessageUtils.addMessage(TOGGLE_OFF);
        }
        this.working = working;
    }

    public void switchOnOff(@Nullable Block block) {
        if (this.isDisabled() || !Config.INSTANCE.isAllowBlock(block))
            return;
        this.switchOnOff();
    }

    public void switchOnOff() {
        if (this.isWorking()) {
            this.clearTask();
            this.setWorking(false);
        } else {
            if (gameMode.isCreative()) { // 仅生存模式开启
                MessageUtils.addMessage(FAIL_MISSING_SURVIVAL);
                return;
            }
            this.setWorking(true);
            if (!client.isInSingleplayer()) {   // 服务器开启时发送警告提示
                MessageUtils.addMessage(WARN_MULTIPLAYER);
            }
        }
    }

    private boolean isDisabled() {
        return Config.INSTANCE.disable;
    }

    public ArrayList<Task> getPendingTasks() {
        return pendingTasks;
    }

    public @Nullable Task getCurrentTask() {
        return currentTask;
    }
}
