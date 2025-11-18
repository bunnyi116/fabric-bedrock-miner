package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.api.ITaskManager;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.CombinedIterator;
import com.github.bunnyi116.bedrockminer.util.InventoryManagerUtils;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.block.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.player.PlayerLookManager;
import com.github.bunnyi116.bedrockminer.util.player.PlayerUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;
import static com.github.bunnyi116.bedrockminer.I18n.*;

public class TaskManager implements ITaskManager {
    private static volatile @Nullable TaskManager INSTANCE;
    private final ArrayList<Task> pendingBlockTasks = new ArrayList<>();
    private final List<TaskRegion> pendingRegionTasks = new ArrayList<>();
    private @Nullable Task currentTask;
    private boolean running;
    private boolean processing;
    private boolean bedrockMinerFeatureEnable = true;
    private int resetCount;

    public void tick() {
        if (!gameVariableIsValid()) {
            return;
        }
        if (Config.getInstance().disable || !this.isRunning()) {
            PlayerLookManager.INSTANCE.tick();
            return;
        }

        if (this.pendingBlockTasks.isEmpty()
                && this.pendingRegionTasks.isEmpty()
                && Config.getInstance().ranges.isEmpty()) {
            this.currentTask = null;
            return;
        }

        if (!isAllowExecutionEnvironment(currentTask != null)) return;

        if (this.currentTask != null) {
            // 当玩家不在处理范围时, 等待40TICK约2秒时间, 如果玩家未回处理位置, 将重新选择任务
            int resetCountMax = 20;
            if (this.resetCount++ >= resetCountMax) {
                // 检查现有任务, 如果只有一个任务, 就没必要重新选择新任务了(因为不存在其他任务)
                if (this.pendingBlockTasks.size() > 1 || !this.pendingRegionTasks.isEmpty() || !Config.getInstance().ranges.isEmpty()) {
                    this.currentTask = null;
                    this.resetCount = 0;
                }
            } else if (this.currentTask.world == world && this.currentTask.canInteractWithBlockAt()) {
                this.processing = true;
                this.currentTask.tick();
                this.resetCount = 0;    // 执行一次TICK, 进行重置
                this.processing = false;
                if (this.currentTask.isComplete()) {
                    this.pendingBlockTasks.remove(this.currentTask);
                    this.currentTask = null;
                } else {
                    return; // 任务没有处理完成, 返回等待下一个TICK继续处理
                }
            } else {
                MessageUtils.setOverlayMessage(Text.literal("远离当前正在处理的方块位置，冷却TICK剩余: " + (resetCountMax - resetCount)));
            }
        }

        // 没有正在处理的任务, 准备选择一个新的任务
        if (this.currentTask == null) {
            final var iterator1 = pendingBlockTasks.iterator();
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

        // 没有正在处理的任务, 准备选择一个新的任务
        if (this.currentTask == null) {
            // 组合迭代器(避免创建新的数组, 浪费内存)
            final var iterator2 = new CombinedIterator<>(Config.getInstance().ranges, pendingRegionTasks);
            while (iterator2.hasNext()) {
                var range = iterator2.next();
                if (!range.isForWorld(world)) continue;
                final BlockPos playerBlockPos = player.getBlockPos();
                final BlockBox rangeBox = BlockBox.create(range.pos1, range.pos2);
                final BlockBox playerBox = new BlockBox(playerBlockPos);
                final double playerBlockInteractionRange = PlayerUtils.getBlockInteractionRange();
                final int radius = (int) Math.ceil(playerBlockInteractionRange) - 1;
                final BlockBox playerExpandBox = playerBox.expand(radius);
                if (!rangeBox.intersects(playerExpandBox)) continue;
                for (int dy = radius; dy > -playerBlockInteractionRange; dy--) {
                    for (int dx = -radius; dx <= playerBlockInteractionRange; dx++) {
                        for (int dz = -radius; dz <= playerBlockInteractionRange; dz++) {
                            final BlockPos blockPos = playerBlockPos.add(dx, dy, dz);
                            if (!PlayerUtils.canInteractWithBlockAt(blockPos, 1.0F)) {
                                continue;
                            }
                            final BlockState blockState = world.getBlockState(blockPos);
                            final Block block = blockState.getBlock();
                            if (blockState.isAir() || BlockUtils.isReplaceable(blockState)) {
                                continue;
                            }
                            if (!Config.getInstance().isAllowBlock(block)) {
                                continue;
                            }
                            if (Config.getInstance().isFloorsBlacklist(blockPos)) {
                                continue;
                            }
                            final Task task = new Task(world, block, blockPos);
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
        if (!InventoryManagerUtils.canInstantlyMinePiston()) {
            msg = FAIL_MISSING_INSTANTMINE;
        }
        if (msg != null && setOverlayMessage) {
            MessageUtils.setOverlayMessage(msg);
            return false;
        }
        return true;
    }

    @Override
    public void addBlockTask(ClientWorld world, BlockPos pos, Block block) {
        if (Config.getInstance().disable || !isRunning()) {
            return;
        }
        if (!isAllowExecutionEnvironment(true)) {
            return;
        }
        if (!gameMode.isSurvivalLike()) {
            return;
        }
        if (!Config.getInstance().isAllowBlock(block)) {
            return;
        }
        if (Config.getInstance().isFloorsBlacklist(pos)) {  // 楼层限制
            var msg = FLOOR_BLACK_LIST_WARN.getString().replace("(#floor#)", String.valueOf(pos.getY()));
            MessageUtils.setOverlayMessage(Text.literal(msg));
            return;
        }
        for (var targetBlock : pendingBlockTasks) {
            if (targetBlock.pos.equals(pos)) {
                return;
            }
        }
        pendingBlockTasks.add(new Task(world, block, pos));
    }

    @Override
    public void removeBlockTask(ClientWorld world, BlockPos pos) {
        final var iterator = pendingBlockTasks.iterator();
        while (iterator.hasNext()) {
            var task = iterator.next();
            if (task.pos.equals(pos)) {
                iterator.remove();
                return;
            }
        }
    }

    @Override
    public void removeBlockTaskAll() {
        pendingBlockTasks.clear();
    }

    @Override
    public void addRegionTask(String name, ClientWorld world, BlockPos pos1, BlockPos pos2) {
        for (TaskRegion range : this.pendingRegionTasks) {
            if (range.name.equals(name)) {
                return;
            }
        }
        this.pendingRegionTasks.add(new TaskRegion(name, world, pos1, pos2));
    }

    @Override
    public void removeRegionTaskAll(String name) {
        final var iterator = pendingRegionTasks.iterator();
        while (iterator.hasNext()) {
            var range = iterator.next();
            if (range.name.equals(name)) {
                iterator.remove();
                return;
            }
        }
    }

    @Override
    public void removeRegionTaskAll() {
        pendingRegionTasks.clear();
    }

    public void removeAll() {
        removeBlockTaskAll();
        removeRegionTaskAll();
        MessageUtils.addMessage(COMMAND_TASK_CLEAR);
    }

    public void switchToggle(@Nullable Block block) {
        if (Config.getInstance().disable || !Config.getInstance().isAllowBlock(block))
            return;
        this.switchToggle();
    }

    @Override
    public void switchToggle() {
        if (this.isRunning()) {
            this.removeAll();
            this.setRunning(false);
        } else {
            if (gameMode.isCreative()) { // 仅生存模式开启
                MessageUtils.addMessage(FAIL_MISSING_SURVIVAL);
                return;
            }
            this.setRunning(true);
            if (!client.isInSingleplayer()) {   // 服务器开启时发送警告提示
                MessageUtils.addMessage(WARN_MULTIPLAYER);
            }
        }
    }

    @Override
    public void setRunning(boolean running) {
        this.setRunning(running, true);
    }

    @Override
    public void setRunning(boolean running, boolean showMessage) {
        if (showMessage) {
            if (running) {
                MessageUtils.addMessage(TOGGLE_ON);
            } else {
                MessageUtils.addMessage(TOGGLE_OFF);
            }
        }
        this.running = running;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isProcessing() {
        return processing;
    }

    @Override
    public boolean isInTasks(ClientWorld world, BlockPos pos) {
        for (var targetBlock : pendingBlockTasks) {
            if (targetBlock.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable Task getCurrentTask() {
        return currentTask;
    }

    @Override
    public boolean isBedrockMinerFeatureEnable() {
        return bedrockMinerFeatureEnable;
    }

    @Override
    public void setBedrockMinerFeatureEnable(boolean bedrockMinerFeatureEnable) {
        this.bedrockMinerFeatureEnable = bedrockMinerFeatureEnable;
    }

    public List<Task> getPendingBlockTasks() {
        return pendingBlockTasks;
    }

    public List<TaskRegion> getPendingRegionTasks() {
        return pendingRegionTasks;
    }

    public static TaskManager getInstance() {
        if (INSTANCE == null) {
            synchronized (TaskManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TaskManager();
                }
            }
        }
        return INSTANCE;
    }

    //region 为 BiliXWhite/litematica-printer 提供兼容方法 (作者更新不及时)
    public static void addTask(Block block, BlockPos pos, ClientWorld world) {
        TaskManager.getInstance().addBlockTask(world, pos, block);
    }

    public static boolean isWorking() {
        return TaskManager.getInstance().isRunning();
    }

    public static void setWorking(boolean working) {
        TaskManager.getInstance().setRunning(working);
    }

    public static void clearTask() {
        TaskManager.getInstance().removeAll();
    }
    //endregion

}
