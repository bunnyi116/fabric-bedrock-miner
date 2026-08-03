package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.*;
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

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
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

    @Getter
    private long ticks;
    private long lastWarningTick;


    public void tick() {
        this.ticks++;
        if (!gameVariableIsValid()) {
            return;
        }
        if (Config.getInstance().disable || !this.isRunning()) {
            this.tick();
            return;
        }
        if (this.pendingBlockTasks.isEmpty() && this.pendingRegionTasks.isEmpty() && Config.getInstance().ranges.isEmpty()) {
            this.removeBlockTaskAll();
            return;
        }
        if (activeBlockTasks.isEmpty() && !isAllowExecutionEnvironment(true)) {
            warnMissingResources();
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

                if (currentTask.active >= resetCountMax) {
                    if (this.pendingBlockTasks.size() > 1 || !this.pendingRegionTasks.isEmpty() || !Config.getInstance().ranges.isEmpty()) {
                        this.cacheBlockTasks.add(currentTask);
                        iterator.remove();
                        currentTask.active = 0;
                        continue;
                    }
                } else if (currentTask.world != level || !currentTask.canInteractWithBlockAt()) {
                    MessageUtils.setOverlayMessage(Component.literal("远离当前正在处理的方块位置, 冷却时间剩余: " + (resetCountMax - ++currentTask.active)));
                    continue;
                }
                processing = true;
                if (TaskLookManager.INSTANCE.getTask() != null && !activeBlockTasks.contains(TaskLookManager.INSTANCE.getTask())) {
                    TaskLookManager.INSTANCE.reset();
                }
                if (TaskLookManager.INSTANCE.isModify()) {
                    if (TaskLookManager.INSTANCE.getTask() != currentTask) {
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
                    case EXECUTE:
                        if (currentTask.planItem != null && !currentTask.planItem.piston.isNeedModify()) {
                            execute = true;
                        } else {
                            return;
                        }
                        break;
                    case RECYCLED_ITEMS:
                        return;
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
                if (TaskLookManager.INSTANCE.isModify() && TaskLookManager.INSTANCE.getTask() != task) {
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
                            if (!PlayerUtils.isWithinBlockInteractionRange(blockPos, 1.0F)) {
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
                            if (TaskLookManager.INSTANCE.isModify() && TaskLookManager.INSTANCE.getTask() != task) {
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

    private boolean isAllowExecutionEnvironment(boolean setOverlayMessage) {
        if (gameType.isCreative() || (gameMode != null && !gameMode.getPlayerMode().isSurvival())) {
            if (setOverlayMessage) {
                MessageUtils.setOverlayMessage(FAIL_MISSING_SURVIVAL);
            }
            return false;
        }

        List<Component> missingList = new ArrayList<>();

        int pistonCount = InventoryUtils.getInventoryItemCount(Items.PISTON);
        if (pistonCount < 2) {
            missingList.add(Component.literal((2 - pistonCount) + "x ").append(Component.translatable(Items.PISTON.getDescriptionId())));
        }

        int torchCount = InventoryUtils.getInventoryItemCount(Items.REDSTONE_TORCH);
        if (torchCount < 1) {
            missingList.add(Component.translatable(Items.REDSTONE_TORCH.getDescriptionId()));
        }

        int slimeCount = InventoryUtils.getInventoryItemCount(Items.SLIME_BLOCK);
        if (slimeCount < 1) {
            missingList.add(Component.translatable(Items.SLIME_BLOCK.getDescriptionId()));
        }

        boolean hasHaste2 = net.minecraft.world.effect.MobEffectUtil.hasDigSpeed(player) && net.minecraft.world.effect.MobEffectUtil.getDigSpeedAmplification(player) >= 1;
        boolean hasPickaxe = canInstantlyMinePistonWithHaste2();

        if (!hasHaste2) {
            missingList.add(FAIL_MISSING_HASTE);
        }
        if (!hasPickaxe) {
            missingList.add(FAIL_MISSING_PICKAXE);
        }

        if (!missingList.isEmpty()) {
            if (setOverlayMessage) {
                net.minecraft.network.chat.MutableComponent msg = Component.empty().append(FAIL_MISSING_PREFIX);
                for (int i = 0; i < missingList.size(); i++) {
                    msg.append(missingList.get(i));
                    if (i < missingList.size() - 1) {
                        msg.append(", ");
                    }
                }
                MessageUtils.setOverlayMessage(msg);
            }
            return false;
        }

        return true;
    }

    private void checkEnvironmentAndPrintToChat() {
        List<Component> missingList = new ArrayList<>();

        int pistonCount = InventoryUtils.getInventoryItemCount(Items.PISTON);
        if (pistonCount < 2) {
            missingList.add(Component.literal((2 - pistonCount) + "x ").append(Component.translatable(Items.PISTON.getDescriptionId())));
        }

        int torchCount = InventoryUtils.getInventoryItemCount(Items.REDSTONE_TORCH);
        if (torchCount < 1) {
            missingList.add(Component.translatable(Items.REDSTONE_TORCH.getDescriptionId()));
        }

        int slimeCount = InventoryUtils.getInventoryItemCount(Items.SLIME_BLOCK);
        if (slimeCount < 1) {
            missingList.add(Component.translatable(Items.SLIME_BLOCK.getDescriptionId()));
        }

        boolean hasHaste2 = net.minecraft.world.effect.MobEffectUtil.hasDigSpeed(player) && net.minecraft.world.effect.MobEffectUtil.getDigSpeedAmplification(player) >= 1;
        boolean hasPickaxe = canInstantlyMinePistonWithHaste2();

        if (!hasHaste2) {
            missingList.add(FAIL_MISSING_HASTE);
        }
        if (!hasPickaxe) {
            missingList.add(FAIL_MISSING_PICKAXE);
        }

        if (!missingList.isEmpty()) {
            net.minecraft.network.chat.MutableComponent msg = Component.empty().append(FAIL_MISSING_PREFIX);
            for (int i = 0; i < missingList.size(); i++) {
                msg.append(missingList.get(i));
                if (i < missingList.size() - 1) {
                    msg.append(", ");
                }
            }
            MessageUtils.addMessage(msg);
        }
    }

    private boolean canInstantlyMinePistonWithHaste2() {
        boolean hasHaste = net.minecraft.world.effect.MobEffectUtil.hasDigSpeed(player);
        int amplifier = hasHaste ? net.minecraft.world.effect.MobEffectUtil.getDigSpeedAmplification(player) : 0;
        float hasteMultiplier = 1.0F + (amplifier + 1) * 0.2F;

        for (net.minecraft.world.item.ItemStack stack : InventoryUtils.getMainStacks(playerInventory)) {
            if (stack.isEmpty()) continue;
            if (!stack.is(Items.DIAMOND_PICKAXE) && !stack.is(Items.NETHERITE_PICKAXE)) {
                continue;
            }
            if (InventoryUtils.isItemDamageWarning(stack, 5)) {
                continue;
            }

            float speed = BlockUtils.getDestroySpeed(net.minecraft.world.level.block.Blocks.PISTON.defaultBlockState(), stack);
            if (hasHaste) {
                speed /= hasteMultiplier;
            }
            speed *= 1.4F;

            float progress = speed / 1.5F / 30.0F;
            if (progress >= 0.7F) {
                return true;
            }
        }
        return false;
    }

    private void warnMissingResources() {
        if (ticks - lastWarningTick >= 100) {
            lastWarningTick = ticks;
            checkEnvironmentAndPrintToChat();
        }
    }

    public void addBlockTask(ClientLevel world, BlockPos pos, Block block) {
        if (Config.getInstance().disable || !isRunning()) {
            return;
        }
        if (!isAllowExecutionEnvironment(true)) {
            warnMissingResources();
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
            if (!minecraft.isLocalServer()) {   // 服务器开启时发送警告提示
                MessageUtils.addMessage(WARN_MULTIPLAYER);
            }
            checkEnvironmentAndPrintToChat();
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