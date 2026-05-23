package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.Debug;
import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.InteractionUtils;
import com.github.bunnyi116.bedrockminer.util.InventoryUtils;
import com.github.bunnyi116.bedrockminer.util.PlayerLookUtils;
import com.github.bunnyi116.bedrockminer.util.PlayerUtils;
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
    // 方案恢复常量（参考 litematica-printer）
    private static final int POWERED_STALL_TICKS = 2;         // 充能卡住冷却等待
    private static final int POST_EXECUTE_TIMEOUT_TICKS = 16; // 执行后同步超时
    private static final int MAX_POWERED_REBUILDS = 3;        // 最大充能重建次数
    private static final int SYNC_GRACE_TICKS = 2;            // 初始化后同步宽限期

    public final ClientLevel world;
    public final Block block;
    public final BlockPos pos;

    private TaskState currentState;
    private TaskState lastState;
    private @Nullable TaskState nextState;
    public final List<Scheme> schemes;
    public @Nullable Scheme activeScheme;
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
    private boolean tickOccupied;
    private boolean requestPickaxe;

    // 充能卡住恢复与执行后同步相关字段
    private int poweredStallCount;       // 充能卡住重建次数
    private int executeTick = -1;        // 执行发生时的 tick
    private int initializeTick = -1;     // 初始化完成时的 tick
    private int lastRepowerTick = -1;    // 上次补充能量的 tick

    public Task(ClientLevel world, Block block, BlockPos pos) {
        this.world = world;
        this.block = block;
        this.pos = pos;
        this.schemes = TaskPlanTools.findAllPossible(pos);
        this.recycledQueue = Queues.newConcurrentLinkedQueue();
        this.init();
    }

    private void tickOccupied() {
        this.tickOccupied = true;
    }

    public boolean canInteractWithBlockAt() {
        if (this.world == BedrockMiner.level) {
            if (PlayerUtils.canInteractWithBlockAt(pos, 1F)) {
                if (activeScheme != null) {
                    return activeScheme.canInteractWithBlockAt();
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

    private void setModifyLook(SchemeBlock blockInfo) {
        if (blockInfo != null) {
            debug("修改视角");
            setModifyLook(blockInfo.facing);
            blockInfo.modify = true;
            this.tickOccupied();
        }
    }

    private void setModifyLook(Direction facing) {
        PlayerLookUtils.set(facing, this);
        this.tickOccupied();
    }

    private void resetModifyLook() {
        if (PlayerLookUtils.isModify()) {
            PlayerLookUtils.reset();
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
        if (activeScheme == null) {
            this.currentState = TaskState.FIND;
            return;
        }
        InteractionUtils.placement(activeScheme.slimeBlock.pos, activeScheme.slimeBlock.facing, Items.SLIME_BLOCK);
        this.addRecycled(activeScheme.slimeBlock.pos);
        this.resetModifyLook();
        this.currentState = TaskState.WAIT_GAME_UPDATE;
    }

    private void placeRedstoneTorch() {
        if (activeScheme == null) {
            this.currentState = TaskState.FIND;
            return;
        }
        debug("红石火把");
        BlockState placeBlockState;
        if (activeScheme.redstoneTorch.facing.getAxis().isVertical()) {
            placeBlockState = Blocks.REDSTONE_TORCH.defaultBlockState();
        } else {
            placeBlockState = Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, activeScheme.redstoneTorch.facing);
        }
        if (InteractionUtils.canPlace(world, activeScheme.redstoneTorch.pos, placeBlockState)) {
            if (activeScheme.redstoneTorch.isNeedModify() && !activeScheme.redstoneTorch.modify) {
                setModifyLook(activeScheme.redstoneTorch);
                return;
            }
            InteractionUtils.placement(activeScheme.redstoneTorch.pos, activeScheme.redstoneTorch.facing, Items.REDSTONE_TORCH);

            BlockState blockState = world.getBlockState(activeScheme.redstoneTorch.pos);
            if (activeScheme.redstoneTorch.facing.getAxis().isHorizontal() && blockState.getBlock() instanceof RedstoneWallTorchBlock) {
                world.setBlock(activeScheme.redstoneTorch.pos,
                        blockState.setValue(RedstoneWallTorchBlock.FACING, activeScheme.redstoneTorch.facing),
                        Block.UPDATE_ALL
                );
            }
            this.addRecycled(activeScheme.redstoneTorch.pos);
            if (Config.getInstance().shortTsk) {
                this.currentState = TaskState.WAIT_GAME_UPDATE;
                this.tickOccupied();
            } else {
                this.setWait(TaskState.WAIT_GAME_UPDATE, 3);
            }
            this.resetModifyLook();
        }
    }

    private void placePiston() {
        if (activeScheme == null) {
            this.currentState = TaskState.FIND;
            return;
        }
        // 打掉附近红石火把(范围处理时候, 不打掉可能会卡主任务失败一直尝试)
        final BlockPos[] nearbyRedstoneTorch = TaskPlanTools.findPistonNearbyRedstoneTorch(activeScheme.piston.pos, world);
        for (final BlockPos pos : nearbyRedstoneTorch) {
            if (world.getBlockState(pos).getBlock() instanceof RedstoneTorchBlock) {
                InteractionUtils.updateBlockBreakingProgress(pos);
            }
        }
        debug("放置活塞");
        BlockState placeBlockState = Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, activeScheme.piston.facing);
        if (InteractionUtils.canPlace(world, activeScheme.piston.pos, placeBlockState)) {
            if (activeScheme.piston.isNeedModify() && !activeScheme.piston.modify) {
                setModifyLook(activeScheme.piston);
                return;
            }
            InteractionUtils.placement(activeScheme.piston.pos, activeScheme.piston.facing, Items.PISTON, Items.STICKY_PISTON);
            BlockState blockState = world.getBlockState(activeScheme.piston.pos);
            if (blockState.getBlock() instanceof PistonBaseBlock) {
                world.setBlock(activeScheme.piston.pos, blockState.setValue(PistonBaseBlock.FACING, activeScheme.piston.facing), Block.UPDATE_ALL);
            }
            this.addRecycled(activeScheme.piston.pos);
            if (Config.getInstance().shortTsk) {
                this.currentState = TaskState.WAIT_GAME_UPDATE;
                this.tickOccupied();
            } else {
                this.setWait(TaskState.WAIT_GAME_UPDATE, 3);
            }
            this.resetModifyLook();
        } else {
            this.activeScheme = null;
            this.currentState = TaskState.FIND;
        }
    }

    private void find() {
        if (this.activeScheme == null) {
            debug("查找方案");
            for (Scheme item : schemes) {
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
                } else if (BlockUtils.sideCoversSmallSquare(item.slimeBlock.pos, item.slimeBlock.facing)) {
                    item.slimeBlock.level -= 1;
                } else {
                    item.slimeBlock.level += 1000;
                }
            }
            schemes.sort(Comparator
                    .comparingInt((Scheme scheme) -> scheme.level + scheme.piston.level + scheme.redstoneTorch.level + scheme.slimeBlock.level)
            );
            for (Scheme item : schemes) {
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
                if (!InteractionUtils.canPlace(world, pistonPos, pistonDefaultState) || !InteractionUtils.canPlace(world, pistonHeadPos, pistonHeadDefaultState)) {
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
                if (InteractionUtils.canPlace(world, item.slimeBlock.pos, Blocks.SLIME_BLOCK.defaultBlockState())
                        || BlockUtils.sideCoversSmallSquare(item.slimeBlock.pos, item.slimeBlock.facing)) {// 特殊放置方案类型1, 需要检查目标方块是否能能被……充
                    if (item.redstoneTorch.type == 1 && !world.getBlockState(pos).isRedstoneConductor(world, pos)) {
                        continue;
                    }
                    // 如果需要放置底座, 检查粘液块是否充足
                    if (BlockUtils.isReplaceable(world.getBlockState(item.slimeBlock.pos))
                            && InventoryUtils.getInventoryItemCount(Items.SLIME_BLOCK) < 1) {
//                        MessageUtils.setOverlayMessage(FAIL_MISSING_SLIME);
                        continue;
                    }
                    // 检查是否与活跃任务冲突
                    if (isSchemeConflictingWithActiveTasks(item, pos)) {
                        continue;
                    }
                    if (TaskManager.getInstance().isBedrockMinerFeatureEnable() && item.redstoneTorch.type == 1) {
                        continue;
                    }
                    {
                        this.activeScheme = item;
                        break;
                    }
                }
            }
        }
        if (this.activeScheme == null) {
            this.currentState = TaskState.FAIL;
            MessageUtils.setOverlayMessage(Component.literal(I18n.HANDLE_SEEK.getString().replace("%BlockPos%", pos.toShortString())));
        } else {
            this.debug("目标: %s", pos);
            this.debug("方案: %s", this.activeScheme.direction);
            this.debug("活塞: %s", this.activeScheme.piston);
            this.debug("底座: %s", this.activeScheme.slimeBlock);
            this.debug("红石火把: %s", this.activeScheme.redstoneTorch);
            this.currentState = TaskState.WAIT_GAME_UPDATE;
        }
    }

    private void recycledItems() {
        if (!recycledQueue.isEmpty()) {
            var blockPos = recycledQueue.peek();
            if (blockPos == null) {
                recycledQueue.remove();
                return;
            }
            var blockState = world.getBlockState(blockPos);
            debug("任务物品正在回收: (%s) --> %s", blockPos.toShortString(), blockState.getBlock().getName().getString());
            if (blockState.getBlock().defaultDestroyTime() < 0) {
                recycledQueue.remove();
                return;
            }
            var instant = PlayerUtils.canInstantlyMineBlock(blockState);
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
        if (executed || player == null || activeScheme == null) {
            return;
        }
        this.updateStates();    // 执行前强制再确认一下条件是否充足了
        if (this.currentState != TaskState.EXECUTE) {
            debug("条件不充足，等待更新");
            this.currentState = TaskState.WAIT_GAME_UPDATE;
            this.tickOccupied();
            return;
        }
        if (!executeModify && activeScheme.direction.getAxis().isHorizontal()) {
            this.setModifyLook(activeScheme.direction.getOpposite());
            this.executeModify = true;
            return;
        } else {
            // 切换到工具
            if (!PlayerUtils.canInstantlyMineBlock(world.getBlockState(activeScheme.piston.pos))) {
                InventoryUtils.autoSwitch(world.getBlockState(activeScheme.piston.pos));
                this.requestPickaxe = true;
                this.setWait(TaskState.EXECUTE, 1);
                return;
            } else {
                this.requestPickaxe = false;
            }
            // 打掉附近红石火把
            final BlockPos[] nearbyRedstoneTorch = TaskPlanTools.findPistonNearbyRedstoneTorch(activeScheme.piston.pos, world);
            for (final BlockPos pos : nearbyRedstoneTorch) {
                if (world.getBlockState(pos).getBlock() instanceof RedstoneTorchBlock) {
                    InteractionUtils.updateBlockBreakingProgress(pos);
                }
            }
            if (world.getBlockState(activeScheme.redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock) {
                InteractionUtils.updateBlockBreakingProgress(activeScheme.redstoneTorch.pos);
            }
            InteractionUtils.updateBlockBreakingProgress(activeScheme.piston.pos);
            InteractionUtils.placement(activeScheme.piston.pos, activeScheme.direction.getOpposite(), Items.PISTON, Items.STICKY_PISTON);
            this.addRecycled(activeScheme.piston.pos);
            if (this.executeModify) {
                this.resetModifyLook();
            }
            this.requestPickaxe = false;
            this.executed = true;
            this.executeTick = tickTotalCount;
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
        if (this.activeScheme == null) {
            this.currentState = TaskState.FIND;
            this.debugUpdateStates("没有正在执行的放置方案, 准备查找可执行方案");
            return;
        }
        if (world.getBlockState(activeScheme.piston.pos).is(Blocks.MOVING_PISTON)) {
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
            if (BlockUtils.isReplaceable(world.getBlockState(this.activeScheme.piston.pos))) {
                this.debugUpdateStates("[%s] [%s] 活塞未放置且该位置可放置物品,设置放置状态", this.activeScheme.piston.pos.toShortString(), this.activeScheme.piston.facing);
                this.currentState = TaskState.PLACE_PISTON;
                return;
            }

            BlockPos pistonHeadPos = activeScheme.piston.pos.relative(activeScheme.piston.facing);
            if (world.getBlockState(activeScheme.piston.pos).getBlock() instanceof PistonBaseBlock) {
                if (!world.getBlockState(this.activeScheme.piston.pos).hasProperty(PistonBaseBlock.EXTENDED)
                        && world.getBlockState(pistonHeadPos).getBlock() instanceof PistonHeadBlock) {
                    return;
                }
            }


//            if (world.getBlockState(this.activeScheme.piston.pos).getBlock() instanceof PistonBaseBlock) {
//                if (world.getBlockState(this.activeScheme.piston.pos).get(PistonBaseBlock.FACING) != this.activeScheme.piston.facing) {
//                    this.debugUpdateStates("[%s] [%s] 活塞已放置, 但放置方向不正确", this.activeScheme.piston.pos.toShortString(), this.activeScheme.piston.facing);
//                    this.currentState = TaskState.FAIL;
//                    return;
//                }
//            }
            // 底座
            if (BlockUtils.isReplaceable(world.getBlockState(this.activeScheme.slimeBlock.pos))) {
                this.debugUpdateStates("[%s] [%s] 底座未放置且该位置可放置物品,设置放置状态", this.activeScheme.slimeBlock.pos.toShortString(), this.activeScheme.slimeBlock.facing);
                this.currentState = TaskState.PLACE_SLIME_BLOCK;
                return;
            }
            if (!BlockUtils.sideCoversSmallSquare(this.activeScheme.slimeBlock.pos, this.activeScheme.slimeBlock.facing)) {
                this.debugUpdateStates("[%s] [%s] 底座已放置, 但不是完整的方块", this.activeScheme.slimeBlock.pos.toShortString(), this.activeScheme.slimeBlock.facing);
                this.currentState = TaskState.FAIL;
                return;
            }
            // 红石火把
            if (BlockUtils.isReplaceable(world.getBlockState(this.activeScheme.redstoneTorch.pos))) {
                this.debugUpdateStates("[%s] [%s] 红石火把未放置且该位置可放置物品,设置放置状态", this.activeScheme.redstoneTorch.pos.toShortString(), this.activeScheme.redstoneTorch.facing);
                this.currentState = TaskState.PLACE_REDSTONE_TORCH;
                return;
            }
//            if (world.getBlockState(this.activeScheme.redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock) {
//                boolean b = false;
//                if (world.getBlockState(this.activeScheme.redstoneTorch.pos).getBlock() instanceof WallRedstoneTorchBlock) {
//                    if (world.getBlockState(this.activeScheme.redstoneTorch.pos).get(WallRedstoneTorchBlock.FACING) != this.activeScheme.redstoneTorch.facing) {
//                        b = true;
//                    }
//                } else if (this.activeScheme.redstoneTorch.facing != Direction.UP) {
//                    b = true;
//                }
//                if (b) {
//                    this.debugUpdateStates("[%s] [%s] 红石火把已放置, 但放置状态与方案不一致", this.activeScheme.redstoneTorch.pos.toShortString(), this.activeScheme.redstoneTorch.facing);
//                    this.currentState = TaskState.FAIL;
//                }
//            }
            if (world.getBlockState(this.activeScheme.piston.pos).getBlock() instanceof PistonBaseBlock) {
                if (world.getBlockState(this.activeScheme.piston.pos).hasProperty(PistonBaseBlock.EXTENDED)) {
                    this.debugUpdateStates("[%s] [%s] 条件已充足, 准备开始尝试", this.activeScheme.piston.pos.toShortString(), this.activeScheme.piston.facing);
                    this.currentState = TaskState.EXECUTE;
                    return;
                }
            }
            // 无法确认状态, 独占等待更新
            this.debugUpdateStates("？？？");
            this.tickOccupied();
        } else {
            // 执行后状态检查（参考 litematica-printer 的健壮恢复机制）
            handlePostExecuteState();
        }
    }

    /** 执行后状态检查——处理充能卡住与同步超时 */
    private void handlePostExecuteState() {
        var pistonBlockState = world.getBlockState(this.activeScheme.piston.pos);

        // 活塞已伸出但基岩未破 → 充能卡住，需要拔掉火把后重建
        if (pistonBlockState.getBlock() instanceof PistonBaseBlock) {
            if (pistonBlockState.hasProperty(PistonBaseBlock.EXTENDED)) {
                if (poweredStallCount < MAX_POWERED_REBUILDS) {
                    // 需要等待冷却时间
                    if (tickTotalCount - Math.max(lastRepowerTick, 0) < POWERED_STALL_TICKS) {
                        this.debugUpdateStates("充能冷却等待中");
                        this.tickOccupied();
                        return;
                    }
                    this.debugUpdateStates("充能卡住，执行重建 (%s/%s)", poweredStallCount + 1, MAX_POWERED_REBUILDS);
                    recoverFromPoweredStall();
                } else {
                    this.debugUpdateStates("充能重建次数超限");
                    this.currentState = TaskState.FAIL;
                }
                return;
            }
        }

        // 活塞位置已空（已回收），但基岩还存在 → 可能需要重新执行
        if (pistonBlockState.isAir()) {
            if (tickTotalCount - executeTick >= POST_EXECUTE_TIMEOUT_TICKS) {
                this.debugUpdateStates("执行后同步超时，重置尝试");
                this.executed = false;
                this.currentState = TaskState.WAIT_GAME_UPDATE;
            } else {
                this.tickOccupied();
            }
            return;
        }

        // 初始化宽限期：刚初始化完成，等待状态同步
        if (!this.executed && initializeTick >= 0 && tickTotalCount - initializeTick <= SYNC_GRACE_TICKS) {
            this.debugUpdateStates("初始化同步宽限期");
            this.tickOccupied();
            return;
        }

        // 其他不明状态，等待
        this.tickOccupied();
    }

    /** 充能卡住恢复：打断红石火把 → 回收活塞 → 重新放置 → 重置执行状态 */
    private void recoverFromPoweredStall() {
        // 打断红石火把以切断充能信号
        if (world.getBlockState(this.activeScheme.redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock) {
            InteractionUtils.updateBlockBreakingProgress(this.activeScheme.redstoneTorch.pos);
        }
        // 打断活塞（回收）
        InteractionUtils.updateBlockBreakingProgress(this.activeScheme.piston.pos);

        // 重新放置活塞
        BlockState placeBlockState = Blocks.PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, this.activeScheme.piston.facing);
        if (InteractionUtils.canPlace(world, this.activeScheme.piston.pos, placeBlockState)) {
            InteractionUtils.placement(this.activeScheme.piston.pos, this.activeScheme.piston.facing, Items.PISTON, Items.STICKY_PISTON);
        }

        // 重新放置红石火把
        if (BlockUtils.isReplaceable(world.getBlockState(this.activeScheme.redstoneTorch.pos))) {
            InteractionUtils.placement(this.activeScheme.redstoneTorch.pos, this.activeScheme.redstoneTorch.facing, Items.REDSTONE_TORCH);
        }

        this.poweredStallCount++;
        this.lastRepowerTick = tickTotalCount;
        this.executed = false;
        this.requestPickaxe = false;
        this.tickOccupied();
    }

    private void init() {
        // 清理附近已有的活塞（避免干扰当前任务）
        clearNearbyPistons();

        if (TaskManager.getInstance().isBedrockMinerFeatureEnable()) {
            this.retryCountMax = 1;
        }
        this.nextState = null;
        this.tickTotalCount = 0;
        this.ticksTotalMax = 100;
        this.ticksTimeoutMax = 25;
        this.tickWaitMax = 0;
        this.activeScheme = null;
        this.recycledQueue.clear();
        this.executed = false;
        this.requestPickaxe = false;
        this.recycled = false;
        this.timeout = false;
        this.currentState = TaskState.WAIT_GAME_UPDATE;
        this.retry = false;
        this.poweredStallCount = 0;
        this.executeTick = -1;
        this.initializeTick = -1;
        this.lastRepowerTick = -1;
        this.find();
    }

    /** 清理目标方块周围已有活塞 */
    private void clearNearbyPistons() {
        var activeTasks = TaskManager.getInstance().getActiveBlockTasks();
        for (final Direction direction : Direction.values()) {
            for (BlockPos checkPos : new BlockPos[]{pos.relative(direction), pos.relative(direction).above()}) {
                BlockState pistonState = world.getBlockState(checkPos);
                if (!(pistonState.getBlock() instanceof PistonBaseBlock)) continue;
                if (!PlayerUtils.canInstantlyMineBlock(pistonState)) continue;
                // 检查是否被活跃任务占用
                if (!activeTasks.isEmpty() && isPosReservedByActiveTask(checkPos, activeTasks)) {
                    continue;
                }
                InteractionUtils.updateBlockBreakingProgress(checkPos, false);
            }
        }
    }

    /** 检查某位置是否被其他活跃任务占用 */
    private static boolean isPosReservedByActiveTask(BlockPos pos, List<Task> activeTasks) {
        for (Task task : activeTasks) {
            if (task == null || task.activeScheme == null) continue;
            if (pos.equals(task.activeScheme.piston.pos)) return true;
            if (pos.equals(task.activeScheme.redstoneTorch.pos)) return true;
            if (pos.equals(task.activeScheme.redstoneTorch.pos.relative(task.activeScheme.redstoneTorch.facing.getOpposite())))
                return true;
        }
        return false;
    }

    /** 检查当前方案是否与活跃任务占用的位置冲突 */
    private boolean isSchemeConflictingWithActiveTasks(Scheme scheme, BlockPos selfPos) {
        var activeTasks = TaskManager.getInstance().getActiveBlockTasks();
        if (activeTasks.isEmpty()) return false;

        // 收集方案的所有关键位置
        final BlockPos headPos = scheme.piston.pos.relative(scheme.piston.facing);
        final BlockPos basePos = scheme.redstoneTorch.pos.relative(scheme.redstoneTorch.facing.getOpposite());

        for (Task task : activeTasks) {
            if (task == null || task.activeScheme == null || task == this) continue;

            final Scheme other = task.activeScheme;
            // 目标位置被占用
            if (selfPos.equals(other.piston.pos)) return true;
            // 活塞位置冲突
            if (scheme.piston.pos.equals(other.piston.pos)
                    || scheme.piston.pos.equals(other.redstoneTorch.pos)
                    || scheme.piston.pos.equals(other.slimeBlock.pos)) return true;
            // 活塞头位置冲突
            if (headPos.equals(other.piston.pos)
                    || headPos.equals(other.redstoneTorch.pos)
                    || headPos.equals(other.slimeBlock.pos)) return true;
            // 红石火把位置冲突
            if (scheme.redstoneTorch.pos.equals(other.piston.pos)
                    || scheme.redstoneTorch.pos.equals(other.redstoneTorch.pos)
                    || scheme.redstoneTorch.pos.equals(other.slimeBlock.pos)) return true;
            // 底座位置冲突
            if (basePos.equals(other.piston.pos)
                    || basePos.equals(other.slimeBlock.pos)) return true;
            // 粘液块位置冲突
            if (scheme.slimeBlock.pos.equals(other.piston.pos)
                    || scheme.slimeBlock.pos.equals(other.redstoneTorch.pos)) return true;
        }
        return false;
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
        if (this.activeScheme != null) {
            return this.activeScheme.piston.isNeedModify() || this.activeScheme.redstoneTorch.isNeedModify();
        }
        return false;
    }
}