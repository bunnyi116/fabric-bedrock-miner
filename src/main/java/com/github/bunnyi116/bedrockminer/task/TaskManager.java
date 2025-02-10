package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import net.minecraft.block.Block;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;
import static com.github.bunnyi116.bedrockminer.I18n.*;

public class TaskManager {
    public static final TaskManager INSTANCE = new TaskManager();
    private boolean enabled = false;
    private final List<Task> tasks = new ArrayList<>();
    private @Nullable Task currentTask = null;
    private final int processCoolingMax = 40;
    private int processCooling = 0;

    public boolean isAllowBlock(Block block, BlockPos blockPos, boolean overlayMessage) {
        if (Config.INSTANCE.floorsBlacklist != null && !Config.INSTANCE.floorsBlacklist.isEmpty()) {
            if (Config.INSTANCE.floorsBlacklist.contains(blockPos.getY())) {
                if (overlayMessage) {
                    var msg = FLOOR_BLACK_LIST_WARN.getString().replace("(#floor#)", String.valueOf(blockPos.getY()));
                    MessageUtils.setOverlayMessage(Text.literal(msg));
                }
                return false;
            }
        }
        // 方块黑名单检查(服务器)
        if (!client.isInSingleplayer()) {
            for (var defaultBlockBlack : Config.INSTANCE.blockBlacklistServer) {
                if (BlockUtils.getBlockId(block).equals(defaultBlockBlack)) {
                    return false;
                }
            }
        }
        // 方块白名单检查(用户自定义)
        for (var blockBlack : Config.INSTANCE.blockWhitelist) {
            if (BlockUtils.getBlockId(block).equals(blockBlack)) {
                return true;
            }
        }
        return false;
    }

    public void switchOnOff(ClientWorld world, BlockPos pos, Block block) {
        if (!this.isAllowBlock(block, pos, false)) {
            return;
        }
        if (enabled) {
            this.setEnabled(false);
        } else {
            if (gameMode.isCreative()) {
                MessageUtils.addMessage(FAIL_MISSING_SURVIVAL);
                return;
            }
            this.setEnabled(true);
            if (!client.isInSingleplayer()) {   // 玩家服务器
                MessageUtils.addMessage(WARN_MULTIPLAYER);
            }
        }
    }

    public void addTask(ClientWorld world, BlockPos pos, Block block) {
        if (!this.enabled) {
            return;
        }
        if (gameMode.isSurvivalLike() && isAllowBlock(block, pos, true)) {
            var task = new Task(world, pos, block);
            if (!tasks.contains(task)) {
                tasks.add(task);
            }
        }
    }

    public void tick() {
        if (!this.enabled) {
            TaskPlayerLookManager.INSTANCE.tickAutoReset();
            return;
        }
        if (this.currentTask != null) {
            if (this.currentTask.canInteractWithBlockAt()) {
                this.currentTask.tick();
                if (this.currentTask.isComplete()) {
                    this.tasks.remove(this.currentTask);
                    this.currentTask = null;
                    this.processCooling = 0;
                    TaskPlayerLookManager.INSTANCE.reset(true, true);
                }
            } else {
                if (this.processCooling++ >= this.processCoolingMax) {
                    this.currentTask = null;
                    this.processCooling = 0;
                } else {
                    MessageUtils.setOverlayMessage(Text.literal(String.format("[%s/%s] 开始进入冷却, 请返回当前任务方块处理范围, 超过冷却时间将切换到下一个任务", this.processCooling, this.processCoolingMax)));
                }
            }
        }
        if (this.currentTask == null && !this.tasks.isEmpty()) {
            for (Task task : this.tasks) {
                if (task.canInteractWithBlockAt()) {
                    this.currentTask = task;
                    this.currentTask.tick();
                    if (this.currentTask.isComplete()) {
                        this.tasks.remove(this.currentTask);
                        this.currentTask = null;
                        this.processCooling = 0;
                    }
                    break;
                }
            }
        }
    }

    public void setEnabled(boolean enabled) {
        if (enabled) {
            MessageUtils.addMessage(TOGGLE_ON);
        } else {
            MessageUtils.addMessage(TOGGLE_OFF);
        }
        this.enabled = enabled;
    }

    public void switchOnOff() {
        this.setEnabled(!this.enabled);
    }
}
