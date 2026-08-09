package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.Debug;
import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.*;
import com.google.common.collect.Queues;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Queue;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;

public class Task {
    public final ClientLevel world;
    public final Block block;
    public final BlockPos pos;

    private TaskState currentState;
    private TaskState lastState;
    private @Nullable TaskState nextState;

    public final List<TaskPlan> planItems;
    public @Nullable TaskPlan planItem;
    public final Queue<BlockPos> recycledQueue;

    public boolean executeModify;
    private int tickTotalCount;
    private int tickInternalCount;
    private int ticksTotalMax;
    private int ticksTimeoutMax;
    private int tickWaitMax;
    public int retryCount;
    public int retryCountMax;
    public int active;
    public boolean retry;
    public boolean executed;
    public boolean recycled;
    public boolean timeout;
    private boolean tickOccupied;   // 当前TICK已被占用
    private boolean requestPickaxe;

    public Task(ClientLevel world, Block block, BlockPos pos) {
        this.world = world;
        this.block = block;
        this.pos = pos;
        this.planItems = TaskPlanTools.findAllPossible(pos);
        this.recycledQueue = Queues.newConcurrentLinkedQueue();
        this.init();
    }

    private void tickOccupied() {
        this.tickOccupied = true;
    }

    public boolean canInteractWithBlockAt() {
        if (this.world == BedrockMiner.level) {
            if (PlayerUtils.isWithinBlockInteractionRange(pos, 1F)) {
                if (planItem != null) {
                    return planItem.canInteractWithBlockAt();
                }
                return true;
            }
        }
        return false;
    }

    private void setWait(@Nullable TaskState nextState, int tickWaitMax) {
        this.nextState = nextState;
        this.tickWaitMax = Math.max(tickWaitMax, 1);
        this.currentState = TaskState.WAIT_CUSTOM;
        this.tickOccupied();
    }

    private void setModifyLook(TaskPlanItem blockInfo) {
        if (blockInfo != null) {
            debug("修改视角");
            setModifyLook(blockInfo.facing);
            blockInfo.modify = true;
            this.tickOccupied();
        }
    }

    private void setModifyLook(Direction facing) {
        TaskLookManager.INSTANCE.set(facing, this);
        this.tickOccupied();
    }

    private void resetModifyLook() {
        if (TaskLookManager.INSTANCE.isModify()) {
            TaskLookManager.INSTANCE.reset();
        }
    }

    public void tick() {
        debug("开始");
        this.tickOccupied = false;  // 重置状态
        if (this.currentState == TaskState.COMPLETE) {
            debug("任务已完成");
        } else {
            this.lastState = this.currentState; // 先将现有状态记录（debug输出）
            if (this.tickTotalCount >= this.ticksTotalMax) {
                this.currentState = TaskState.COMPLETE;
                this.tickOccupied();
            }
            if (!this.timeout && this.tickTotalCount >= this.ticksTimeoutMax) {
                this.timeout = true;
                this.currentState = TaskState.TIMEOUT;
            }
            this.tickInternalCount = 0;
            while (tickInternalCount < 10) {
                this.lastState = this.currentState;
                switch (this.currentState) {
                    case INITIALIZE:
                        this.init();
                        break;
                    case WAIT_GAME_UPDATE:
                        this.updateStates();
                        break;
                    case WAIT_CUSTOM:
                        this.waitCustom();
                        break;
                    case FIND:
                        this.find();
                        break;
                    case PLACE_PISTON:
                        this.placePiston();
                        break;
                    case PLACE_REDSTONE_TORCH:
                        this.placeRedstoneTorch();
                        break;
                    case PLACE_SLIME_BLOCK:
                        this.placeSlimeBlock();
                        break;
                    case EXECUTE:
                        this.execute();
                        break;
                    case RETRY:
                        retry = true;
                        if (!this.recycledQueue.isEmpty()) {
                            this.currentState = TaskState.RECYCLED_ITEMS;
                            return;
                        }
                        if (this.retryCount < this.retryCountMax) {
                            this.retryCount++;
                            this.debug("任务物品回收已完成, 超时重试: %s", retryCount);
                            this.currentState = TaskState.INITIALIZE;
                        } else {
                            this.currentState = TaskState.COMPLETE;
                            this.tickOccupied();
                        }
                        break;
                    case TIMEOUT:
                        debug("任务已超时");
                        currentState = TaskState.RETRY;
                        break;
                    case FAIL:
                        debug("任务已失败");
                        currentState = TaskState.RETRY;
                        break;
                    case RECYCLED_ITEMS:
                        this.recycledItems();
                        break;
                    case COMPLETE:
                        debug("任务已完成");
                        break;
                }
                if (this.lastState == this.currentState) {  // 开始状态与结束状态一致, 避免无意义的内循环
                    debug("状态一致，无需内部循环");
                    break;
                }
                if (this.tickOccupied) {
                    debug("独占TICK运行");
                    break;
                }
                ++tickInternalCount;
            }
        }
        debug("结束\r\n");
        ++tickTotalCount;
    }

    private void placeSlimeBlock() {
        if (planItem == null) {
            this.currentState = TaskState.FIND;
            return;
        }
        InteractionUtils.placement(planItem.slimeBlock.pos, planItem.slimeBlock.facing, Items.SLIME_BLOCK);
        this.addRecycled(planItem.slimeBlock.pos);
        this.resetModifyLook();
        this.currentState = TaskState.WAIT_GAME_UPDATE;
    }

    private void placeRedstoneTorch() {
        if (planItem == null) {
            this.currentState = TaskState.FIND;
            return;
        }
        debug("红石火把");
        BlockState placeBlockState;
        if (planItem.redstoneTorch.facing.getAxis().isVertical()) {
            placeBlockState = Blocks.REDSTONE_TORCH.defaultBlockState();
        } else {
            placeBlockState = Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, planItem.redstoneTorch.facing);
        }
        if (BlockUtils.canPlace(planItem.redstoneTorch.pos, placeBlockState)) {
            if (planItem.redstoneTorch.isNeedModify() && !planItem.redstoneTorch.modify) {
                setModifyLook(planItem.redstoneTorch);
                return;
            }
            InteractionUtils.placement(planItem.redstoneTorch.pos, planItem.redstoneTorch.facing, Items.REDSTONE_TORCH);

            BlockState blockState = world.getBlockState(planItem.redstoneTorch.pos);
            if (planItem.redstoneTorch.facing.getAxis().isHorizontal() && blockState.getBlock() instanceof RedstoneWallTorchBlock) {
                world.setBlock(planItem.redstoneTorch.pos,
                        blockState.setValue(RedstoneWallTorchBlock.FACING, planItem.redstoneTorch.facing),
                        Block.UPDATE_ALL
                );
            }
            this.addRecycled(planItem.redstoneTorch.pos);
            if (Config.getInstance().fast) {
                this.currentState = TaskState.WAIT_GAME_UPDATE;
                this.tickOccupied();
            } else {
                this.setWait(TaskState.WAIT_GAME_UPDATE, 3);
            }
            this.resetModifyLook();
        }
    }

    private void placePiston() {
        if (planItem == null) {
            this.currentState = TaskState.FIND;
            return;
        }
        // 打掉附近红石火把(范围处理时候, 不打掉可能会卡主任务失败一直尝试)
        final BlockPos[] nearbyRedstoneTorch = TaskPlanTools.findPistonNearbyRedstoneTorch(planItem.piston.pos, world);
        for (final BlockPos pos : nearbyRedstoneTorch) {
            if (world.getBlockState(pos).getBlock() instanceof RedstoneTorchBlock) {
                InteractionUtils.updateBlockBreakingProgress(pos);
            }
        }
        debug("放置活塞");
        BlockState placeBlockState = Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, planItem.piston.facing);
        if (BlockUtils.canPlace(planItem.piston.pos, placeBlockState)) {
            if (planItem.piston.isNeedModify() && !planItem.piston.modify) {
                setModifyLook(planItem.piston);
                return;
            }
            InteractionUtils.placement(planItem.piston.pos, planItem.piston.facing, Items.PISTON, Items.STICKY_PISTON);
            BlockState blockState = world.getBlockState(planItem.piston.pos);
            if (blockState.getBlock() instanceof PistonBaseBlock) {
                world.setBlock(planItem.piston.pos, blockState.setValue(PistonBaseBlock.FACING, planItem.piston.facing), Block.UPDATE_ALL);
            }
            this.addRecycled(planItem.piston.pos);
            if (Config.getInstance().fast) {
                this.currentState = TaskState.WAIT_GAME_UPDATE;
                this.tickOccupied();
            } else {
                this.setWait(TaskState.WAIT_GAME_UPDATE, 3);
            }
            this.resetModifyLook();
        } else {
            this.planItem = null;
            this.currentState = TaskState.FIND;
        }
    }

    private void find() {
        if (this.planItem == null) {
            debug("查找方案");
            for (TaskPlan item : planItems) {
                BlockState slimeBlockState = world.getBlockState(item.slimeBlock.pos);
                if (item.canInteractWithBlockAt()) {
                    item.slimeBlock.level -= 1;
                } else {
                    item.slimeBlock.level += 1000;
                }
                if (InventoryUtils.getInventoryItemCount(Items.SLIME_BLOCK) < 1) {
                    item.slimeBlock.level += 1000;
                } else if (BlockUtils.isReplaceable(slimeBlockState)) {
                    item.slimeBlock.level += 1;
                } else if (BlockUtils.canSupportCenter(item.slimeBlock.pos, item.slimeBlock.facing)) {
                    item.slimeBlock.level -= 1;
                } else {
                    item.slimeBlock.level += 1000;
                }
            }
            planItems.sort(Comparator
                    .comparingInt((TaskPlan scheme) -> scheme.level + scheme.piston.level + scheme.redstoneTorch.level + scheme.slimeBlock.level)
            );
            for (TaskPlan item : planItems) {
                if (!item.isWorldValid()) {
                    continue;
                }
                final BlockPos pistonPos = item.piston.pos;
                final Direction pistonFacing = item.piston.facing;
                final BlockPos pistonHeadPos = pistonPos.relative(pistonFacing);
                final BlockState pistonState = world.getBlockState(pistonPos);
                final BlockState pistonHeadState = world.getBlockState(pistonHeadPos);
                final BlockState pistonDefaultState = Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, pistonFacing);
                final BlockState pistonHeadDefaultState = Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, pistonFacing);
                if (!BlockUtils.canPlace(pistonPos, pistonDefaultState) || !BlockUtils.canPlace(pistonHeadPos, pistonHeadDefaultState)) {
                    if (!(pistonState.is(Blocks.PISTON) && pistonHeadState.is(Blocks.PISTON_HEAD))) {
                        continue;
                    }
                }
                final BlockState redstoneTorchState = world.getBlockState(item.redstoneTorch.pos);
                if (!BlockUtils.isReplaceable(redstoneTorchState)) {  // 如果该位置已存在方块
                    // 当前位置方块类型
                    if (!(redstoneTorchState.getBlock() instanceof RedstoneTorchBlock
                            || redstoneTorchState.getBlock() instanceof RedstoneWallTorchBlock
                    )) {
                        continue;
                    }
                }
                if (world.getFluidState(item.redstoneTorch.pos).is(FluidTags.WATER)) {
                    continue;
                }
                if (BlockUtils.canPlace(item.slimeBlock.pos, Blocks.SLIME_BLOCK.defaultBlockState())
                        || BlockUtils.canSupportCenter(item.slimeBlock.pos, item.slimeBlock.facing)) {// 特殊放置方案类型1, 需要检查目标方块是否能能被……充
                    if (item.redstoneTorch.type == 1 && !world.getBlockState(pos).isRedstoneConductor(world, pos)) {
                        continue;
                    }
                    // 如果需要放置底座, 检查粘液块是否充足
                    if (BlockUtils.isReplaceable(world.getBlockState(item.slimeBlock.pos))
                            && InventoryUtils.getInventoryItemCount(Items.SLIME_BLOCK) < 1) {
//                        MessageUtils.setOverlayMessage(FAIL_MISSING_SLIME);
                        continue;
                    }
                    boolean b = true;
                    if (!TaskManager.getInstance().getActiveBlockTasks().isEmpty()) {
                        for (Task task : TaskManager.getInstance().getActiveBlockTasks()) {
                            if (task == null) continue;
                            if (task.planItem == null) continue;
                            if (pos.equals(task.planItem.piston.pos)) {
                                b = false;
                                break;
                            }
                            if (item.piston.pos.equals(task.planItem.piston.pos)) {
                                b = false;
                                break;
                            }
                            if (item.piston.pos.equals(task.planItem.redstoneTorch.pos)) {
                                b = false;
                                break;
                            }
                            for (Direction direction : Direction.values()) {
                                if (item.piston.pos.equals(task.planItem.redstoneTorch.pos.relative(direction))) {
                                    b = false;
                                    break;
                                }
                            }
                            if (item.piston.pos.equals(task.planItem.slimeBlock.pos)) {
                                b = false;
                                break;
                            }

                            if (item.redstoneTorch.pos.equals(task.planItem.piston.pos)) {
                                b = false;
                                break;
                            }
                            for (Direction direction : Direction.values()) {
                                if (item.redstoneTorch.pos.equals(task.planItem.piston.pos.relative(direction))) {
                                    b = false;
                                    break;
                                }
                            }
                            if (item.redstoneTorch.pos.equals(task.planItem.redstoneTorch.pos)) {
                                b = false;
                                break;
                            }
                            if (item.redstoneTorch.pos.equals(task.planItem.slimeBlock.pos)) {
                                b = false;
                                break;
                            }


                            if (item.slimeBlock.pos.equals(task.planItem.piston.pos)) {
                                b = false;
                                break;
                            }
                            if (item.slimeBlock.pos.equals(task.planItem.redstoneTorch.pos)) {
                                b = false;
                                break;
                            }

                            if (item.piston.pos.relative(item.piston.facing).equals(task.planItem.piston.pos)) {
                                b = false;
                                break;
                            }
                            if (item.piston.pos.relative(item.piston.facing).equals(task.planItem.redstoneTorch.pos)) {
                                b = false;
                                break;
                            }
                            if (item.piston.pos.relative(item.piston.facing).equals(task.planItem.slimeBlock.pos)) {
                                b = false;
                                break;
                            }
                        }
                    }
                    if (TaskManager.getInstance().isBedrockMinerFeatureEnable() && item.redstoneTorch.type == 1) {
                        continue;
                    }
                    if (b) {
                        this.planItem = item;
                        break;
                    }
                }
            }
        }
        if (this.planItem == null) {
            this.currentState = TaskState.FAIL;
            MessageUtils.setOverlayMessage(Component.literal(I18n.HANDLE_SEEK.getString().replace("%BlockPos%", pos.toShortString())));
        } else {
            this.debug("目标: %s", pos);
            this.debug("方案: %s", this.planItem.direction);
            this.debug("活塞: %s", this.planItem.piston);
            this.debug("底座: %s", this.planItem.slimeBlock);
            this.debug("红石火把: %s", this.planItem.redstoneTorch);
            this.currentState = TaskState.WAIT_GAME_UPDATE;
        }
    }

    private void recycledItems() {
        if (!recycledQueue.isEmpty()) {
            BlockPos blockPos = recycledQueue.peek();
            if (blockPos == null) {
                recycledQueue.remove();
                return;
            }
            BlockState blockState = world.getBlockState(blockPos);
            debug("任务物品正在回收: (%s) --> %s", blockPos.toShortString(), blockState.getBlock().getName().getString());
            if (blockState.getBlock().defaultDestroyTime() < 0) {
                recycledQueue.remove();
                return;
            }
            boolean instant = BlockUtils.canInstantlyMineBlock(blockState);
            if (!instant) {
                this.requestPickaxe = true;
                InventoryUtils.autoSwitch(blockState);
            } else {
                this.requestPickaxe = false;
            }
            InteractionUtils.updateBlockBreakingProgress(blockPos, false);
            if (BlockUtils.isReplaceable(blockState)) {
                recycledQueue.remove();
            }
            if (!instant && !recycledQueue.isEmpty()) {
                this.tickOccupied();
            }
        } else {
            debug("任务物品回收已完成");
            if (retry) {
                currentState = TaskState.RETRY;
            } else {
                currentState = TaskState.COMPLETE;
            }
            this.tickOccupied();
        }
    }

    private void execute() {
        if (executed || player == null || planItem == null) {
            return;
        }
        this.updateStates();    // 执行前强制再确认一下条件是否充足了
        if (this.currentState != TaskState.EXECUTE) {
            debug("条件不充足，等待更新");
            this.currentState = TaskState.WAIT_GAME_UPDATE;
            this.tickOccupied();
            return;
        }
        if (!executeModify && planItem.direction.getAxis().isHorizontal()) {
            this.setModifyLook(planItem.direction.getOpposite());
            this.executeModify = true;
            return;
        } else {
            // 切换到工具
            if (!BlockUtils.canInstantlyMineBlock(world.getBlockState(planItem.piston.pos))) {
                InventoryUtils.autoSwitch(world.getBlockState(planItem.piston.pos));
                this.requestPickaxe = true;
                this.setWait(TaskState.EXECUTE, 1);
                return;
            } else {
                this.requestPickaxe = false;
            }
            // 打掉附近红石火把
            final BlockPos[] nearbyRedstoneTorch = TaskPlanTools.findPistonNearbyRedstoneTorch(planItem.piston.pos, world);
            for (final BlockPos pos : nearbyRedstoneTorch) {
                if (world.getBlockState(pos).getBlock() instanceof RedstoneTorchBlock) {
                    InteractionUtils.updateBlockBreakingProgress(pos);
                }
            }
            if (world.getBlockState(planItem.redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock) {
                InteractionUtils.updateBlockBreakingProgress(planItem.redstoneTorch.pos);
            }
            InteractionUtils.updateBlockBreakingProgress(planItem.piston.pos);
            InteractionUtils.placement(planItem.piston.pos, planItem.direction.getOpposite(), Items.PISTON, Items.STICKY_PISTON);
            this.addRecycled(planItem.piston.pos);
            if (this.executeModify) {
                this.resetModifyLook();
            }
            this.requestPickaxe = false;
            this.executed = true;
            this.tickOccupied();
        }
        this.currentState = TaskState.WAIT_GAME_UPDATE;
    }

    private void waitCustom() {
        if (--this.tickWaitMax <= 0) {
            this.currentState = this.nextState == null ? TaskState.WAIT_GAME_UPDATE : this.nextState;
            this.tickWaitMax = 0;
            this.debug("等待已结束, 状态设置为: %s", this.currentState);
        } else {
            ++this.ticksTotalMax;
            ++this.ticksTimeoutMax;
            this.tickOccupied();
            this.debug("剩余等待TICK: %s", tickWaitMax);
        }
    }

    private void updateStates() {
        if (!world.getBlockState(pos).is(block)) {
            this.currentState = TaskState.RECYCLED_ITEMS;
            this.debugUpdateStates("目标不存在");
            this.tickOccupied();
            return;
        }
        if (this.planItem == null) {
            this.currentState = TaskState.FIND;
            this.debugUpdateStates("没有正在执行的放置方案, 准备查找可执行方案");
            return;
        }
        if (world.getBlockState(planItem.piston.pos).is(Blocks.MOVING_PISTON)) {
            this.debugUpdateStates("活塞正在移动");
            this.tickOccupied();
            return;
        }
        if (!this.executed) {
            debugUpdateStates("任务未执行过");

            if (!canInteractWithBlockAt()) {
                this.debugUpdateStates("当前放置方案不在交互范围内, 准备重新选择任务");
                this.currentState = TaskState.FIND;
                this.tickOccupied();
                return;
            }

            // 活塞
            if (BlockUtils.isReplaceable(world.getBlockState(this.planItem.piston.pos))) {
                this.debugUpdateStates("[%s] [%s] 活塞未放置且该位置可放置物品,设置放置状态", this.planItem.piston.pos.toShortString(), this.planItem.piston.facing);
                this.currentState = TaskState.PLACE_PISTON;
                return;
            }

            BlockPos pistonHeadPos = planItem.piston.pos.relative(planItem.piston.facing);
            if (world.getBlockState(planItem.piston.pos).getBlock() instanceof PistonBaseBlock) {
                if (!world.getBlockState(this.planItem.piston.pos).hasProperty(PistonBaseBlock.EXTENDED)
                        && world.getBlockState(pistonHeadPos).getBlock() instanceof PistonHeadBlock) {
                    return;
                }
            }


//            if (world.getBlockState(this.planItem.piston.pos).getBlock() instanceof PistonBaseBlock) {
//                if (world.getBlockState(this.planItem.piston.pos).get(PistonBaseBlock.FACING) != this.planItem.piston.facing) {
//                    this.debugUpdateStates("[%s] [%s] 活塞已放置, 但放置方向不正确", this.planItem.piston.pos.toShortString(), this.planItem.piston.facing);
//                    this.currentState = TaskState.FAIL;
//                    return;
//                }
//            }
            // 底座
            if (BlockUtils.isReplaceable(world.getBlockState(this.planItem.slimeBlock.pos))) {
                this.debugUpdateStates("[%s] [%s] 底座未放置且该位置可放置物品,设置放置状态", this.planItem.slimeBlock.pos.toShortString(), this.planItem.slimeBlock.facing);
                this.currentState = TaskState.PLACE_SLIME_BLOCK;
                return;
            }
            if (!BlockUtils.canSupportCenter(this.planItem.slimeBlock.pos, this.planItem.slimeBlock.facing)) {
                this.debugUpdateStates("[%s] [%s] 底座已放置, 但不是完整的方块", this.planItem.slimeBlock.pos.toShortString(), this.planItem.slimeBlock.facing);
                this.currentState = TaskState.FAIL;
                return;
            }
            // 红石火把
            if (BlockUtils.isReplaceable(world.getBlockState(this.planItem.redstoneTorch.pos))) {
                this.debugUpdateStates("[%s] [%s] 红石火把未放置且该位置可放置物品,设置放置状态", this.planItem.redstoneTorch.pos.toShortString(), this.planItem.redstoneTorch.facing);
                this.currentState = TaskState.PLACE_REDSTONE_TORCH;
                return;
            }
//            if (world.getBlockState(this.planItem.redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock) {
//                boolean b = false;
//                if (world.getBlockState(this.planItem.redstoneTorch.pos).getBlock() instanceof WallRedstoneTorchBlock) {
//                    if (world.getBlockState(this.planItem.redstoneTorch.pos).get(WallRedstoneTorchBlock.FACING) != this.planItem.redstoneTorch.facing) {
//                        b = true;
//                    }
//                } else if (this.planItem.redstoneTorch.facing != Direction.UP) {
//                    b = true;
//                }
//                if (b) {
//                    this.debugUpdateStates("[%s] [%s] 红石火把已放置, 但放置状态与方案不一致", this.planItem.redstoneTorch.pos.toShortString(), this.planItem.redstoneTorch.facing);
//                    this.currentState = TaskState.FAIL;
//                }
//            }
            if (world.getBlockState(this.planItem.piston.pos).getBlock() instanceof PistonBaseBlock) {
                if (world.getBlockState(this.planItem.piston.pos).hasProperty(PistonBaseBlock.EXTENDED)) {
                    this.debugUpdateStates("[%s] [%s] 条件已充足, 准备开始尝试", this.planItem.piston.pos.toShortString(), this.planItem.piston.facing);
                    this.currentState = TaskState.EXECUTE;
                    return;
                }
            }
            // 无法确认状态, 独占等待更新
            this.debugUpdateStates("？？？");
            this.tickOccupied();
        }
    }

    private void init() {
        for (final Direction direction : Direction.values()) {
            BlockPos pos1 = pos.relative(direction);
            BlockPos pos2 = pos1.above();
            BlockState pistonState = world.getBlockState(pos1);
            if (pistonState.getBlock() instanceof PistonBaseBlock && BlockUtils.canInstantlyMineBlock(pistonState)) {
                if (!TaskManager.getInstance().getActiveBlockTasks().isEmpty()) {
                    for (Task task : TaskManager.getInstance().getActiveBlockTasks()) {
                        if (task == null) continue;
                        if (task.planItem == null) continue;
                        if (pos1.equals(task.planItem.piston.pos)) {
                            return;
                        }
                    }
                }
                InteractionUtils.updateBlockBreakingProgress(pos1, false);
            }
            BlockState pistonUpState = world.getBlockState(pos2);
            if (pistonUpState.getBlock() instanceof PistonBaseBlock && BlockUtils.canInstantlyMineBlock(pistonUpState)) {
                if (!TaskManager.getInstance().getActiveBlockTasks().isEmpty()) {
                    for (Task task : TaskManager.getInstance().getActiveBlockTasks()) {
                        if (task == null) continue;
                        if (task.planItem == null) continue;
                        if (pos2.equals(task.planItem.piston.pos)) {
                            return;
                        }
                    }
                }
                InteractionUtils.updateBlockBreakingProgress(pos2, false);
            }
        }
        if (TaskManager.getInstance().isBedrockMinerFeatureEnable()) {
            this.retryCountMax = 1;
        }
        this.nextState = null;
        this.tickTotalCount = 0;
        this.ticksTotalMax = 100;
        this.ticksTimeoutMax = 25;
        this.tickWaitMax = 0;
        this.planItem = null;
        this.recycledQueue.clear();
        this.executed = false;
        this.requestPickaxe = false;
        this.recycled = false;
        this.timeout = false;
        this.currentState = TaskState.WAIT_GAME_UPDATE;
        this.retry = false;
        this.find();
    }

    private void debug(String var1, Object... var2) {
        Debug.write("[{}/{}] [{}:{}/{}] [{} -> {}] {}",
                retryCount, retryCountMax,
                tickTotalCount, tickInternalCount, ticksTotalMax,
                lastState, currentState,
                String.format(var1, var2));
    }

    private void debugUpdateStates(String var1, Object... var2) {
        Debug.write("[{}/{}] [{}:{}/{}] [{} -> {}] [状态更新] {}",
                retryCount, retryCountMax,
                tickTotalCount, tickInternalCount, ticksTotalMax,
                lastState, currentState,
                String.format(var1, var2));
    }

    private void addRecycled(BlockPos pos) {
        if (!recycledQueue.contains(pos)) {
            recycledQueue.add(pos);
        }
    }

    public boolean isRequestPickaxe() {
        return requestPickaxe;
    }

    public boolean isComplete() {
        return currentState == TaskState.COMPLETE || tickTotalCount >= ticksTotalMax;
    }

    public TaskState getCurrentState() {
        return currentState;
    }

    public boolean isNeedModify() {
        if (this.planItem != null) {
            return this.planItem.piston.isNeedModify() || this.planItem.redstoneTorch.isNeedModify();
        }
        return false;
    }
}