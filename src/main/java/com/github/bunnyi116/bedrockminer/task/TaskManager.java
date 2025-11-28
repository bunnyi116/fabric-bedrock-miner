package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.CombinedIterator;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.block.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.player.PlayerInventoryUtils;
import com.github.bunnyi116.bedrockminer.util.player.PlayerLookUtils;
import com.github.bunnyi116.bedrockminer.util.player.PlayerUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;
import static com.github.bunnyi116.bedrockminer.I18n.*;

public class TaskManager {
    private static volatile @Nullable TaskManager INSTANCE;
    private final ArrayList<Task> pendingBlockTasks = new ArrayList<>();
    private final LinkedHashMap<Task, Integer> activeBlockTasks = new LinkedHashMap<>();
    private final ArrayList<Task> cacheBlockTasks = new ArrayList<>();
    private final List<TaskRegion> pendingRegionTasks = new ArrayList<>();
    private boolean running;
    private boolean processing;
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
        if (!this.pendingBlockTasks.isEmpty()) {
            if (sortCount > 0) {
                sortCount--;
            } else {
                sortCount = 40;
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
        }
        boolean execute = false;
        if (!this.activeBlockTasks.isEmpty()) {
            int resetCountMax = 10;
            Iterator<Map.Entry<Task, Integer>> iterator = this.activeBlockTasks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Task, Integer> entry = iterator.next();
                if (entry == null) continue;
                Task currentTask = entry.getKey();
                if (currentTask.world != world || !currentTask.canInteractWithBlockAt()) {
                    if (entry.getValue() > resetCountMax) {

                        // 检查现有任务, 如果只有一个任务, 就没必要重新选择新任务了(因为不存在其他任务)
                        if (this.pendingBlockTasks.size() > 1 || !this.pendingRegionTasks.isEmpty() || !Config.getInstance().ranges.isEmpty()) {
                            this.cacheBlockTasks.add(currentTask);
                            iterator.remove();
                            continue;
                        }
                    } else {
                        MessageUtils.setOverlayMessage(Text.literal("远离当前正在处理的方块位置, 冷却时间剩余: " + (resetCountMax - entry.getValue())));
                        entry.setValue(entry.getValue() + 1);
                    }
                    continue;
                }
                processing = true;
                if (!execute && PlayerLookUtils.getTask() != null && !activeBlockTasks.containsKey(PlayerLookUtils.getTask())) {
                    PlayerLookUtils.reset();
                }
                if (!execute && PlayerLookUtils.isModify() && PlayerLookUtils.getTask() != currentTask) {
                    continue;
                }
                if (execute && currentTask.getCurrentState() != TaskState.EXECUTE) {
                    continue;
                }
                if (execute && (currentTask.planItem == null || currentTask.planItem.piston.isNeedModify())) {
                    continue;
                }
                currentTask.tick(activeBlockTasks);
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

                entry.setValue(0);
                processing = false;
                if (currentTask.isComplete()) {
                    iterator.remove();
                    this.pendingBlockTasks.remove(currentTask);
                }
                if (PlayerLookUtils.isModify()) {
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
                BlockState blockState = world.getBlockState(task.pos);
                if (blockState.isAir() || BlockUtils.isReplaceable(blockState)) {
                    continue;
                }
                if (task.world != world) {
                    continue;
                }
                if (!task.canInteractWithBlockAt()) {
                    continue;
                }
                if (PlayerLookUtils.isModify() && PlayerLookUtils.getTask() != task) {
                    continue;
                }
                iterator.remove();
                this.activeBlockTasks.put(task, 0);
                if (this.activeBlockTasks.size() >= Config.getInstance().limitMax) {
                    return;
                }
            }
        }

        double playerBlockInteractionRange = PlayerUtils.getBlockInteractionRange();
        int radius = (int) Math.ceil(playerBlockInteractionRange);

        // 没有正在处理的任务, 准备选择一个新的任务
        Iterator<Task> iterator = this.pendingBlockTasks.iterator();
        while (iterator.hasNext() && this.activeBlockTasks.size() < Config.getInstance().limitMax) {
            Task task = iterator.next();
            if (task == null) continue;
            final BlockState blockState = world.getBlockState(task.pos);
            final Block block = blockState.getBlock();
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
            if (PlayerLookUtils.isModify() && PlayerLookUtils.getTask() != task) {
                continue;
            }
            if (task.world != world) {
                iterator.remove();
            }
            if (!this.activeBlockTasks.containsKey(task)) {
                this.activeBlockTasks.put(task, 0);
            }
        }


        // 没有正在处理的任务, 准备选择一个新的任务
        if (this.activeBlockTasks.size() < Config.getInstance().limitMax) {
            // 组合迭代器(避免创建新的数组, 浪费内存)
            final CombinedIterator<TaskRegion> iterator2 = new CombinedIterator<>(Config.getInstance().ranges, pendingRegionTasks);
            while (iterator2.hasNext()) {
                TaskRegion range = iterator2.next();
                if (!range.isForWorld(world)) continue;
                final BlockBox rangeBox = BlockBox.create(range.pos1, range.pos2);
                final BlockBox playerBox = new BlockBox(player.getBlockPos());
                final BlockBox playerExpandBox = playerBox.expand(radius);
                if (!rangeBox.intersects(playerExpandBox)) continue;

                for (int dy = radius; dy > -radius; dy--) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            final BlockPos blockPos = player.getBlockPos().add(dx, dy, dz);
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
                            if (PlayerLookUtils.isModify() && PlayerLookUtils.getTask() != task) {
                                continue;
                            }
                            if (task.world != world) {
                                iterator2.remove();
                                continue;
                            }
                            if (!this.activeBlockTasks.containsKey(task)) {
                                this.activeBlockTasks.put(task, 0);
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
        Text msg = null;
        if (gameMode.isCreative()) {
            msg = FAIL_MISSING_SURVIVAL;
        }
        if (client.interactionManager != null && !client.interactionManager.getCurrentGameMode().isSurvivalLike()) {
            msg = FAIL_MISSING_SURVIVAL;
        }
        if (PlayerInventoryUtils.getInventoryItemCount(Items.PISTON) < 2) {
            msg = FAIL_MISSING_PISTON;
        }
        if (PlayerInventoryUtils.getInventoryItemCount(Items.REDSTONE_TORCH) < 1) {
            msg = FAIL_MISSING_REDSTONETORCH;
        }
        if (!PlayerInventoryUtils.canInstantlyMinePiston()) {
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
            String msg = FLOOR_BLACK_LIST_WARN.getString().replace("(#floor#)", String.valueOf(pos.getY()));
            MessageUtils.setOverlayMessage(Text.literal(msg));
            return;
        }
        for (Task targetBlock : pendingBlockTasks) {
            if (targetBlock.pos.equals(pos)) {
                return;
            }
        }
        pendingBlockTasks.add(new Task(world, block, pos));
    }

    public void removeBlockTask(ClientWorld world, BlockPos pos) {
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

    public void addRegionTask(String name, ClientWorld world, BlockPos pos1, BlockPos pos2) {
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

    public boolean isRunning() {
        return running;
    }

    public boolean isProcessing() {
        return processing;
    }

    public boolean isInTasks(ClientWorld world, BlockPos pos) {
        for (Task targetBlock : pendingBlockTasks) {
            if (targetBlock.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public boolean isBedrockMinerFeatureEnable() {
        return bedrockMinerFeatureEnable;
    }

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
