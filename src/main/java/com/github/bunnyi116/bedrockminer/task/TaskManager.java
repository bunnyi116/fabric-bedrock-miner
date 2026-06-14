package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.CombinedIterator;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.InventoryUtils;
import com.github.bunnyi116.bedrockminer.util.PlayerLookUtils;
import com.github.bunnyi116.bedrockminer.util.PlayerUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;
import static com.github.bunnyi116.bedrockminer.I18n.*;

public class TaskManager {
    private static volatile @Nullable TaskManager INSTANCE;
    private final ArrayList<Task> pendingBlockTasks = new ArrayList<>();
    @Getter
    private final ArrayList<Task> activeBlockTasks = new ArrayList<>();
    @Getter
    private final ArrayList<Task> cacheBlockTasks = new ArrayList<>();
    @Getter
    private final List<TaskRegion> pendingRegionTasks = new ArrayList<>();
    @Getter
    private boolean running;
    @Getter
    private boolean processing;
    @Setter
    @Getter
    private boolean bedrockMinerFeatureEnable = true;
    private int sortCount;

    public void tick() {
        if (!gameVariableIsValid()) {
            return;
        }
        if (Config.getInstance().disable || !this.isRunning()) {
            PlayerLookUtils.tick();
            return;
        }
        if (this.pendingBlockTasks.isEmpty() && this.pendingRegionTasks.isEmpty() && Config.getInstance().ranges.isEmpty()) {
            this.removeBlockTaskAll();
            return;
        }
        if (!isAllowExecutionEnvironment(activeBlockTasks.isEmpty())) {
            return;
        }
        // 每40TICK进行排序一次
        if (!this.pendingBlockTasks.isEmpty() || !this.activeBlockTasks.isEmpty()) {
            if (sortCount > 0) {
                sortCount--;
            } else {
                sortCount = 40;
                if (this.pendingBlockTasks.size() > 1) {
                    this.pendingBlockTasks.sort((a1, a2) -> {
                        // 首先按Y坐标降序排列（高的优先）
                        int dy = a2.pos.getY() - a1.pos.getY();
                        // 如果Y坐标不同，直接返回比较结果
                        if (dy != 0) {
                            return dy;
                        }
                        // 如果Y坐标相同，按水平距离升序排列（近的优先）
                        double dist1 = PlayerUtils.getHorizontalDistanceToPlayer(a1.pos);
                        double dist2 = PlayerUtils.getHorizontalDistanceToPlayer(a2.pos);
                        return Double.compare(dist1, dist2);
                    });
                }
                if (this.activeBlockTasks.size() > 1) {
                    this.activeBlockTasks.sort((a1, a2) -> Boolean.compare(a1.isNeedModify(), a2.isNeedModify()));
                }

            }
        }
        boolean execute = false;
        boolean requestPickaxe = false;
        boolean modifyLook = false;
        if (!this.activeBlockTasks.isEmpty()) {
            if (this.activeBlockTasks.size() > 1) {
                for (Task entry : this.activeBlockTasks) {
                    if (entry == null) continue;
                    if (entry.getCurrentState() == TaskState.EXECUTE) {
                        execute = true;
                        break;
                    }
                    if (entry.isRequestPickaxe()) {
                        requestPickaxe = true;
                        break;
                    }
                }
            }
            int resetCountMax = 20;
            Iterator<Task> iterator = this.activeBlockTasks.iterator();
            while (iterator.hasNext()) {
                Task currentTask = iterator.next();
                if (currentTask == null) continue;
                if (currentTask.world != level || !currentTask.canInteractWithBlockAt()) {
                    MessageUtils.setOverlayMessage(Component.literal("远离当前正在处理的方块位置, 冷却时间剩余: " + (resetCountMax - currentTask.active)));
                    continue;
                }
                if (currentTask.active >= resetCountMax) {
                    if (this.pendingBlockTasks.size() > 1 || !this.pendingRegionTasks.isEmpty() || !Config.getInstance().ranges.isEmpty()) {
                        this.cacheBlockTasks.add(currentTask);
                        iterator.remove();
                        currentTask.active = 0;
                        continue;
                    }
                } else {
                    currentTask.active++;
                }
                processing = true;
                if (PlayerLookUtils.getTask() != null && !activeBlockTasks.contains(PlayerLookUtils.getTask())) {
                    PlayerLookUtils.reset();
                }
                if (PlayerLookUtils.isModify()) {
                    if (PlayerLookUtils.getTask() != currentTask) {
                        continue;
                    }
                    modifyLook = true;
                }
                if (execute && currentTask.getCurrentState() != TaskState.EXECUTE) {
                    continue;
                }
                if (requestPickaxe && !currentTask.isRequestPickaxe()) {
                    continue;
                }
                currentTask.tick();
                currentTask.active = 0;
                switch (currentTask.getCurrentState()) {
                    case EXECUTE -> {
                        if (currentTask.planItem != null && !currentTask.planItem.piston.isNeedModify()) {
                            execute = true;
                        } else {
                            return;
                        }
                    }
                    case RECYCLED_ITEMS -> {
                        return;
                    }
                }
                processing = false;
                if (currentTask.isComplete()) {
                    iterator.remove();
                    this.pendingBlockTasks.remove(currentTask);
                    currentTask.active = 0;
                    continue;
                }
                if (modifyLook) {
                    return;
                }
            }
            if (this.activeBlockTasks.size() >= Config.getInstance().limitMax) {
                return;
            }
        }

        // 先检查缓存里面是否有任务存在
        if (!this.cacheBlockTasks.isEmpty()) {
            Iterator<Task> iterator = this.cacheBlockTasks.iterator();
            while (iterator.hasNext()) {
                Task task = iterator.next();
                if (task == null) continue;
                BlockState blockState = level.getBlockState(task.pos);
                if (blockState.isAir() || BlockUtils.isReplaceable(blockState)) {
                    continue;
                }
                if (task.world != level) {
                    continue;
                }
                if (!task.canInteractWithBlockAt()) {
                    continue;
                }
                if (PlayerLookUtils.isModify() && PlayerLookUtils.getTask() != task) {
                    continue;
                }
                iterator.remove();
                this.activeBlockTasks.add(task);
                if (this.activeBlockTasks.size() >= Config.getInstance().limitMax) {
                    return;
                }
                return;
            }
        }

        double playerBlockInteractionRange = PlayerUtils.getBlockInteractionRange();
        int radius = (int) Math.ceil(playerBlockInteractionRange);

        // 没有正在处理的任务, 准备选择一个新的任务
        Iterator<Task> iterator = this.pendingBlockTasks.iterator();
        while (iterator.hasNext() && this.activeBlockTasks.size() < Config.getInstance().limitMax) {
            Task task = iterator.next();
            if (task == null) continue;
            BlockState blockState = level.getBlockState(task.pos);
            Block block = blockState.getBlock();
            if (blockState.isAir() || BlockUtils.isReplaceable(blockState)) {
                continue;
            }
            if (!Config.getInstance().isAllowBlock(block)) {
                continue;
            }
            if (Config.getInstance().isFloorsBlacklist(task.pos)) {
                continue;
            }
            if (!task.canInteractWithBlockAt()) {
                continue;
            }
            if (task.world != level) {
                iterator.remove();
            }
            if (!this.activeBlockTasks.contains(task)) {
                this.activeBlockTasks.add(task);
            }
            if (this.activeBlockTasks.size() >= Config.getInstance().limitMax) {
                return;
            }
        }

        // 没有正在处理的任务, 准备选择一个新的任务
        if (this.activeBlockTasks.size() < Config.getInstance().limitMax) {
            // 组合迭代器(避免创建新的数组, 浪费内存)
            final CombinedIterator<TaskRegion> iterator2 = new CombinedIterator<>(Config.getInstance().ranges, pendingRegionTasks);
            while (iterator2.hasNext()) {
                TaskRegion range = iterator2.next();
                if (!range.isForWorld(level)) continue;
                BoundingBox rangeBox = BoundingBox.fromCorners(range.pos1, range.pos2);
                BoundingBox playerBox = new BoundingBox(player.blockPosition());
                BoundingBox playerExpandBox = playerBox.inflatedBy(radius);
                // 提前判断：如果完全不相交，直接跳过整个循环
                if (!rangeBox.intersects(playerExpandBox)) {
                    continue;
                }
                for (int y = rangeBox.maxY(); y >= rangeBox.minY(); y--) {
                    for (int z = rangeBox.minZ(); z <= rangeBox.maxZ(); z++) {
                        for (int x = rangeBox.minX(); x <= rangeBox.maxX(); x++) {
                            BlockPos blockPos = new BlockPos(x, y, z);
                            if (!PlayerUtils.canInteractWithBlockAt(blockPos, 1.0F)) {
                                continue;
                            }
                            final BlockState blockState = level.getBlockState(blockPos);
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
                            final Task task = new Task(level, block, blockPos);
                            if (!task.canInteractWithBlockAt()) {
                                continue;
                            }
                            if (PlayerLookUtils.isModify() && PlayerLookUtils.getTask() != task) {
                                continue;
                            }
                            if (task.world != level) {
                                iterator2.remove();
                                continue;
                            }
                            if (!this.activeBlockTasks.contains(task)) {
                                this.activeBlockTasks.add(task);
                            }
                            if (this.activeBlockTasks.size() >= Config.getInstance().limitMax) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean isAllowExecutionEnvironment(boolean setOverlayMessage) {
        Component msg = null;
        if (gameType.isCreative()) {
            msg = FAIL_MISSING_SURVIVAL;
        }
        if (gameMode != null && !gameMode.getPlayerMode().isSurvival()) {
            msg = FAIL_MISSING_SURVIVAL;
        }
        if (InventoryUtils.getInventoryItemCount(Items.PISTON) < 2) {
            msg = FAIL_MISSING_PISTON;
        }
        if (InventoryUtils.getInventoryItemCount(Items.REDSTONE_TORCH) < 1) {
            msg = FAIL_MISSING_REDSTONETORCH;
        }
        if (!InventoryUtils.canInstantlyMinePiston()) {
            msg = FAIL_MISSING_INSTANTMINE;
        }
        if (msg != null) {
            if (setOverlayMessage) {
                MessageUtils.setOverlayMessage(msg);
            }
            return false;
        }
        return true;
    }

    public void addBlockTask(ClientLevel world, BlockPos pos, Block block) {
        if (Config.getInstance().disable || !isRunning()) {
            return;
        }
        if (!isAllowExecutionEnvironment(true)) {
            return;
        }
        if (!gameType.isSurvival()) {
            return;
        }
        if (!Config.getInstance().isAllowBlock(block)) {
            return;
        }
        if (Config.getInstance().isFloorsBlacklist(pos)) {  // 楼层限制
            String msg = FLOOR_BLACK_LIST_WARN.getString().replace("(#floor#)", String.valueOf(pos.getY()));
            MessageUtils.setOverlayMessage(Component.literal(msg));
            return;
        }
        for (Task targetBlock : pendingBlockTasks) {
            if (targetBlock.pos.equals(pos)) {
                return;
            }
        }
        pendingBlockTasks.add(new Task(world, block, pos));
    }

    public void removeBlockTask(ClientLevel world, BlockPos pos) {
        final Iterator<Task> iterator = pendingBlockTasks.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (task.pos.equals(pos)) {
                iterator.remove();
                return;
            }
        }
    }

    public void removeBlockTaskAll() {
        this.activeBlockTasks.clear();
        this.cacheBlockTasks.clear();
        this.pendingBlockTasks.clear();
    }

    public void addRegionTask(String name, ClientLevel world, BlockPos pos1, BlockPos pos2) {
        for (TaskRegion range : this.pendingRegionTasks) {
            if (range.name.equals(name)) {
                return;
            }
        }
        this.pendingRegionTasks.add(new TaskRegion(name, world, pos1, pos2));
    }

    public void removeRegionTaskAll(String name) {
        final Iterator<TaskRegion> iterator = pendingRegionTasks.iterator();
        while (iterator.hasNext()) {
            TaskRegion range = iterator.next();
            if (range.name.equals(name)) {
                iterator.remove();
                return;
            }
        }
    }

    public void removeRegionTaskAll() {
        pendingRegionTasks.clear();
    }

    public void removeAll() {
        removeAll(true);
    }

    public void removeAll(boolean showMessage) {
        removeBlockTaskAll();
        removeRegionTaskAll();
        if (showMessage) {
            MessageUtils.addMessage(COMMAND_TASK_CLEAR);
        }
    }

    public void switchToggle(@Nullable Block block) {
        if (Config.getInstance().disable || !Config.getInstance().isAllowBlock(block))
            return;
        this.switchToggle();
    }

    public void switchToggle() {
        if (this.isRunning()) {
            this.removeAll();
            this.setRunning(false);
        } else {
            if (gameType.isCreative()) { // 仅生存模式开启
                MessageUtils.addMessage(FAIL_MISSING_SURVIVAL);
                return;
            }
            this.setRunning(true);
            if (!client.isLocalServer()) {   // 服务器开启时发送警告提示
                MessageUtils.addMessage(WARN_MULTIPLAYER);
            }
        }
    }

    public void setRunning(boolean running) {
        this.setRunning(running, true);
    }

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

    public boolean isInTasks(ClientLevel world, BlockPos pos) {
        for (Task targetBlock : pendingBlockTasks) {
            if (targetBlock.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public List<Task> getPendingBlockTasks() {
        return pendingBlockTasks;
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
    public static void addTask(Block block, BlockPos pos, ClientLevel world) {
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