package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.Debug;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    // 位置冷却（防止同一位置频繁重试，参考 litematica-printer）
    private final Map<BlockPos, Integer> positionCooldowns = new HashMap<>();
    private static final int RETRY_COOLDOWN_TICKS = 6;
    private static final int MAX_ACTIVE_TARGETS = 3; // 最大同时活跃任务数
    private static final int EXECUTE_BUDGET_PER_TICK = 2; // 每tick最多执行次数
    private int sortCount;
    private int executeBudget;

    /** 检查位置是否处于冷却中 */
    private boolean isOnCooldown(BlockPos pos) {
        return positionCooldowns.containsKey(pos.immutable());
    }

    /** 设置位置冷却 */
    private void setCooldown(BlockPos pos) {
        setCooldown(pos, RETRY_COOLDOWN_TICKS);
    }

    private void setCooldown(BlockPos pos, int ticks) {
        positionCooldowns.put(pos.immutable(), ticks);
    }

    /** 逐tick减少冷却计时 */
    private void tickCooldowns() {
        if (positionCooldowns.isEmpty()) return;
        positionCooldowns.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) return true;
            entry.setValue(remaining);
            return false;
        });
    }

    public void tick() {
        tickCooldowns();

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
        executeBudget = EXECUTE_BUDGET_PER_TICK;
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
                currentTask.active++;
                // 超出冷却上限时优先处理（避免溢出，必须在可交互检查之前）
                if (currentTask.active >= resetCountMax) {
                    if (this.pendingBlockTasks.size() > 1 || !this.pendingRegionTasks.isEmpty() || !Config.getInstance().ranges.isEmpty()) {
                        this.cacheBlockTasks.add(currentTask);
                        iterator.remove();
                        currentTask.active = 0;
                        continue;
                    }
                    // 无其他待处理任务时，保持在上限不溢出
                    currentTask.active = resetCountMax;
                }
                if (currentTask.world != level || !currentTask.canInteractWithBlockAt()) {
                    MessageUtils.setOverlayMessage(Component.literal("远离当前正在处理的方块位置, 冷却时间剩余: " + (resetCountMax - currentTask.active)));
                    continue;
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
                        executeBudget--;
                        if (currentTask.activeScheme != null && !currentTask.activeScheme.piston.isNeedModify()) {
                            execute = true;
                        } else {
                            return;
                        }
                        // 消耗完预算则结束本tick处理
                        if (executeBudget <= 0) return;
                    }
                    case RECYCLED_ITEMS -> {
                        return;
                    }
                }
                processing = false;
                if (currentTask.isComplete()) {
                    // 基岩未破则设置冷却，防止立即重试浪费资源
                    if (level.getBlockState(currentTask.pos).is(currentTask.block)) {
                        setCooldown(currentTask.pos);
                    }
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
        Debug.write("addBlockTask called at {} block={} running={}", pos, BlockUtils.getKeyString(block), this.isRunning());
        if (Config.getInstance().disable || !isRunning()) {
            Debug.write("addBlockTask skipped: disable={} running={}", Config.getInstance().disable, this.isRunning());
            return;
        }
        if (!isAllowExecutionEnvironment(true)) {
            Debug.write("addBlockTask skipped: execution environment not allowed");
            return;
        }
        if (!gameType.isSurvival()) {
            Debug.write("addBlockTask skipped: not survival");
            return;
        }
        if (!Config.getInstance().isAllowBlock(block)) {
            Debug.write("addBlockTask skipped: block not allowed");
            return;
        }
        if (Config.getInstance().isFloorsBlacklist(pos)) {
            String msg = FLOOR_BLACK_LIST_WARN.getString().replace("(#floor#)", String.valueOf(pos.getY()));
            MessageUtils.setOverlayMessage(Component.literal(msg));
            Debug.write("addBlockTask skipped: floor blacklist");
            return;
        }
        // 冷却检查：刚处理完的位置需要等待
        if (isOnCooldown(pos)) {
            Debug.write("addBlockTask skipped: on cooldown");
            return;
        }
        for (Task targetBlock : pendingBlockTasks) {
            if (targetBlock.pos.equals(pos)) {
                Debug.write("addBlockTask skipped: already in pending");
                return;
            }
        }
        Debug.write("addBlockTask added {}", pos);
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
        if (Config.getInstance().disable || !Config.getInstance().isAllowBlock(block)) {
            Debug.write("switchToggle(block) skipped: disable={} isAllowBlock={}", Config.getInstance().disable, Config.getInstance().isAllowBlock(block));
            return;
        }
        Debug.write("switchToggle(block) proceeding for {}", BlockUtils.getKeyString(block));
        this.switchToggle();
    }

    public void switchToggle() {
        Debug.write("switchToggle() called, current running={}", this.isRunning());
        if (this.isRunning()) {
            this.removeAll();
            this.setRunning(false);
        } else {
            if (gameType.isCreative()) { // 仅生存模式开启
                MessageUtils.addMessage(FAIL_MISSING_SURVIVAL);
                Debug.write("switchToggle() rejected: creative mode");
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
        Debug.write("setRunning running={} showMessage={} current={}", running, showMessage, this.running);
        if (showMessage) {
            if (running) {
                MessageUtils.addMessage(TOGGLE_ON);
                Debug.write("TOGGLE_ON message sent");
            } else {
                MessageUtils.addMessage(TOGGLE_OFF);
                Debug.write("TOGGLE_OFF message sent");
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