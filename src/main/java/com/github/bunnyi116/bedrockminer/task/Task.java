package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.Debug;
import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.block.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.player.PlayerInteractionUtils;
import com.github.bunnyi116.bedrockminer.util.player.PlayerInventoryUtils;
import com.github.bunnyi116.bedrockminer.util.player.PlayerLookManager;
import com.github.bunnyi116.bedrockminer.util.player.PlayerUtils;
import com.google.common.collect.Queues;
import net.minecraft.block.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;
import static net.minecraft.block.Block.sideCoversSmallSquare;

public class Task {
    public final ClientWorld world;
    public final Block block;
    public final BlockPos pos;

    private TaskState currentState;
    private TaskState lastState;
    private @Nullable TaskState nextState;

    public final List<TaskPlan> planItems;
    public @Nullable TaskPlan planItem;

    public final List<BlockPos> recycledItems;
    public boolean executeModify;
    private int tickTotalCount;
    private int tickInternalCount;
    private int ticksTotalMax;
    private int ticksTimeoutMax;
    private int tickWaitMax;
    public int retryCount = 0;
    public int retryCountMax = 1;
    public boolean retry;
    public boolean executed;
    public boolean recycled;
    public boolean timeout;
    private boolean tickOccupied;   // 当前TICK已被占用

    public Task(ClientWorld world, Block block, BlockPos pos) {
        this.world = world;
        this.block = block;
        this.pos = pos;
        this.planItems = TaskPlanTools.findAllPossible(pos, world);
        this.recycledItems = new ArrayList<>();
        this.init();
    }

    private void tickOccupied() {
        this.tickOccupied = true;
    }

    public boolean canInteractWithBlockAt() {
        if (this.world == BedrockMiner.world) {
            if (PlayerUtils.canInteractWithBlockAt(pos, 1.0F)) {
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
        PlayerLookManager.INSTANCE.set(facing, this);
        this.tickOccupied();
    }

    private void resetModifyLook() {
        if (PlayerLookManager.INSTANCE.isModify()) {
            PlayerLookManager.INSTANCE.reset();
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
                    case INITIALIZE -> this.init();
                    case WAIT_GAME_UPDATE -> this.updateStates();
                    case WAIT_CUSTOM -> this.waitCustom();
                    case FIND -> this.find();
                    case PLACE_PISTON -> this.placePiston();
                    case PLACE_REDSTONE_TORCH -> this.placeRedstoneTorch();
                    case PLACE_SLIME_BLOCK -> this.placeSlimeBlock();
                    case EXECUTE -> this.execute();
                    case RETRY -> {
                        retry = true;
                        if (!recycledItems.isEmpty()) {
                            currentState = TaskState.RECYCLED_ITEMS;
                            return;
                        }
                        if (retryCount < retryCountMax) {
                            retryCount++;
                            debug("任务物品回收已完成, 超时重试: %s", retryCount);
                            currentState = TaskState.INITIALIZE;
                        } else {
                            currentState = TaskState.COMPLETE;
                        }
                    }
                    case TIMEOUT -> {
                        debug("任务已超时");
                        currentState = TaskState.RETRY;
                    }
                    case FAIL -> {
                        debug("任务已失败");
                        currentState = TaskState.RETRY;
                    }
                    case RECYCLED_ITEMS -> this.recycledItems();
                    case COMPLETE -> debug("任务已完成");
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
        PlayerInteractionUtils.placement(planItem.slimeBlock.pos, planItem.slimeBlock.facing, Items.SLIME_BLOCK);
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
            placeBlockState = Blocks.REDSTONE_TORCH.getDefaultState();
        } else {
            placeBlockState = Blocks.REDSTONE_WALL_TORCH.getDefaultState().with(WallRedstoneTorchBlock.FACING, planItem.redstoneTorch.facing);
        }
        if (PlayerInteractionUtils.canPlace(world, planItem.redstoneTorch.pos, placeBlockState)) {
            if (planItem.redstoneTorch.isNeedModify() && !planItem.redstoneTorch.modify) {
                setModifyLook(planItem.redstoneTorch);
                return;
            }
            PlayerInteractionUtils.placement(planItem.redstoneTorch.pos, planItem.redstoneTorch.facing, Items.REDSTONE_TORCH);

            BlockState blockState = world.getBlockState(planItem.redstoneTorch.pos);
            if (planItem.redstoneTorch.facing.getAxis().isHorizontal() && blockState.getBlock() instanceof WallRedstoneTorchBlock) {
                world.setBlockState(planItem.redstoneTorch.pos,
                        blockState.with(WallRedstoneTorchBlock.FACING, planItem.redstoneTorch.facing),
                        Block.NOTIFY_ALL
                );
            }
            this.addRecycled(planItem.redstoneTorch.pos);
            if (planItem.redstoneTorch.isNeedModify()) {
                if (Config.getInstance().shortTsk) {
                    this.setWait(TaskState.WAIT_GAME_UPDATE, 1);
                } else {
                    this.setWait(TaskState.WAIT_GAME_UPDATE, 3);
                }
            } else {
                this.currentState = TaskState.WAIT_GAME_UPDATE;
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
        final var nearbyRedstoneTorch = TaskPlanTools.findPistonNearbyRedstoneTorch(planItem.piston.pos, world);
        for (final var pos : nearbyRedstoneTorch) {
            if (world.getBlockState(pos).getBlock() instanceof RedstoneTorchBlock) {
                PlayerInteractionUtils.updateBlockBreakingProgress(pos);
            }
        }
        debug("放置活塞");
        BlockState placeBlockState = Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, planItem.piston.facing);
        if (PlayerInteractionUtils.canPlace(world, planItem.piston.pos, placeBlockState)) {
            if (planItem.piston.isNeedModify() && !planItem.piston.modify) {
                setModifyLook(planItem.piston);
                return;
            }
            PlayerInteractionUtils.placement(planItem.piston.pos, planItem.piston.facing, Items.PISTON);
            BlockState blockState = world.getBlockState(planItem.piston.pos);
            if (blockState.getBlock() instanceof PistonBlock) {
                world.setBlockState(planItem.piston.pos, blockState.with(PistonBlock.FACING, planItem.piston.facing), Block.NOTIFY_ALL);
            }
            this.addRecycled(planItem.piston.pos);
            if (planItem.piston.isNeedModify()) {
                this.setWait(TaskState.WAIT_GAME_UPDATE, 1);
            } else {
                this.currentState = TaskState.WAIT_GAME_UPDATE;
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
                var slimeBlockState = world.getBlockState(item.slimeBlock.pos);
                if (item.canInteractWithBlockAt()) {
                    item.slimeBlock.level -= 1;
                } else {
                    item.slimeBlock.level += 1000;
                }
                if (PlayerInventoryUtils.getInventoryItemCount(Items.SLIME_BLOCK) < 1) {
                    item.slimeBlock.level += 1000;
                } else if (BlockUtils.isReplaceable(slimeBlockState)) {
                    item.slimeBlock.level += 1;
                } else if (sideCoversSmallSquare(world, item.slimeBlock.pos, item.slimeBlock.facing)) {
                    item.slimeBlock.level -= 1;
                } else {
                    item.slimeBlock.level += 1;
                }
            }
            planItems.sort(Comparator
                    .comparingInt((TaskPlan scheme) -> scheme.level + scheme.piston.level + scheme.redstoneTorch.level + scheme.slimeBlock.level)
            );
            for (TaskPlan item : planItems) {
                if (!item.isWorldValid()) {
                    continue;
                }
                final var pistonPos = item.piston.pos;
                final var pistonFacing = item.piston.facing;
                final var pistonHeadPos = pistonPos.offset(pistonFacing);
                final var pistonState = world.getBlockState(pistonPos);
                final var pistonHeadState = world.getBlockState(pistonHeadPos);
                final var pistonDefaultState = Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, pistonFacing);
                final var pistonHeadDefaultState = Blocks.PISTON_HEAD.getDefaultState().with(PistonHeadBlock.FACING, pistonFacing);
                if (!PlayerInteractionUtils.canPlace(world, pistonPos, pistonDefaultState) || !PlayerInteractionUtils.canPlace(world, pistonHeadPos, pistonHeadDefaultState)) {
                    if (!(pistonState.isOf(Blocks.PISTON) && pistonHeadState.isOf(Blocks.PISTON_HEAD))) {
                        continue;
                    }
                }
                final var redstoneTorchState = world.getBlockState(item.redstoneTorch.pos);
                if (!BlockUtils.isReplaceable(redstoneTorchState)) {  // 如果该位置已存在方块
                    // 当前位置方块类型
                    if (!(redstoneTorchState.getBlock() instanceof RedstoneTorchBlock
                            || redstoneTorchState.getBlock() instanceof WallRedstoneTorchBlock
                    )) {
                        continue;
                    }
                }
                if (world.getFluidState(item.redstoneTorch.pos).isIn(FluidTags.WATER)) {
                    continue;
                }
                if (PlayerInteractionUtils.canPlace(world, item.slimeBlock.pos, Blocks.SLIME_BLOCK.getDefaultState())
                        || sideCoversSmallSquare(world, item.slimeBlock.pos, item.slimeBlock.facing)) {// 特殊放置方案类型1, 需要检查目标方块是否能能被……充
                    if (item.redstoneTorch.type == 1 && !world.getBlockState(pos).isSolidBlock(world, pos)) {
                        continue;
                    }
                    // 如果需要放置底座, 检查粘液块是否充足
                    if (BlockUtils.isReplaceable(world.getBlockState(item.slimeBlock.pos))
                            && PlayerInventoryUtils.getInventoryItemCount(Items.SLIME_BLOCK) < 1) {
//                        MessageUtils.setOverlayMessage(FAIL_MISSING_SLIME);
                        continue;
                    }
                    this.planItem = item;
                    break;
                }
            }
        }
        if (this.planItem == null) {
            this.currentState = TaskState.FAIL;
            MessageUtils.setOverlayMessage(Text.literal(I18n.HANDLE_SEEK.getString().replace("%BlockPos%", pos.toShortString())));
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
        if (!recycledItems.isEmpty()) {
            PlayerInteractionUtils.addBlocksToQueue(recycledItems);
        }
        debug("任务物品回收已完成");
        if (retry) {
            currentState = TaskState.RETRY;
        } else {
            currentState = TaskState.COMPLETE;
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
            if (!PlayerUtils.canInstantlyMineBlock(world.getBlockState(planItem.piston.pos))) {
                PlayerInventoryUtils.autoSwitch(world.getBlockState(planItem.piston.pos));
                this.setWait(TaskState.EXECUTE, 1);
                this.tickOccupied();
                return;
            }
            // 打掉附近红石火把
            final var nearbyRedstoneTorch = TaskPlanTools.findPistonNearbyRedstoneTorch(planItem.piston.pos, world);
            for (final var pos : nearbyRedstoneTorch) {
                if (world.getBlockState(pos).getBlock() instanceof RedstoneTorchBlock) {
                    PlayerInteractionUtils.updateBlockBreakingProgress(pos);
                }
            }
            if (world.getBlockState(planItem.redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock) {
                PlayerInteractionUtils.updateBlockBreakingProgress(planItem.redstoneTorch.pos);
            }
            PlayerInteractionUtils.updateBlockBreakingProgress(planItem.piston.pos, null, () -> {
                PlayerInteractionUtils.placement(planItem.piston.pos, planItem.direction.getOpposite(), Items.PISTON);
            });

            this.addRecycled(planItem.piston.pos);
            if (this.executeModify) {
                this.resetModifyLook();
            }
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
        if (!world.getBlockState(pos).isOf(block)) {
            this.currentState = TaskState.RECYCLED_ITEMS;
            this.debugUpdateStates("目标不存在");
            return;
        }
        if (this.planItem == null) {
            this.currentState = TaskState.FIND;
            this.debugUpdateStates("没有正在执行的放置方案, 准备查找可执行方案");
            return;
        }
        if (world.getBlockState(planItem.piston.pos).isOf(Blocks.MOVING_PISTON)) {
            this.debugUpdateStates("活塞正在移动");
            this.tickOccupied();
            return;
        }
        if (!this.executed) {
            debugUpdateStates("任务未执行过");
            // 活塞
            if (BlockUtils.isReplaceable(world.getBlockState(this.planItem.piston.pos))) {
                this.debugUpdateStates("[%s] [%s] 活塞未放置且该位置可放置物品,设置放置状态", this.planItem.piston.pos.toShortString(), this.planItem.piston.facing);
                this.currentState = TaskState.PLACE_PISTON;
                return;
            }
//            if (world.getBlockState(this.planItem.piston.pos).getBlock() instanceof PistonBlock) {
//                if (world.getBlockState(this.planItem.piston.pos).get(PistonBlock.FACING) != this.planItem.piston.facing) {
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
            if (!Block.sideCoversSmallSquare(world, this.planItem.slimeBlock.pos, this.planItem.slimeBlock.facing)) {
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
            if (world.getBlockState(this.planItem.piston.pos).getBlock() instanceof PistonBlock) {
                if (world.getBlockState(this.planItem.piston.pos).contains(PistonBlock.EXTENDED)) {
                    if (world.getBlockState(this.planItem.piston.pos).get(PistonBlock.EXTENDED)) {
                        this.debugUpdateStates("[%s] [%s] 条件已充足, 准备开始尝试", this.planItem.piston.pos.toShortString(), this.planItem.piston.facing);
                        this.currentState = TaskState.EXECUTE;
                        return;
                    }
                }
            }
            // 无法确认状态, 独占等待更新
            this.debugUpdateStates("？？？");
            this.tickOccupied();
        }
    }

    private void init() {
        for (final var direction : Direction.values()) {
            BlockPos pos1 = pos.offset(direction);
            BlockPos pos2 = pos1.up();
            BlockState pistonState = world.getBlockState(pos1);
            if (pistonState.getBlock() instanceof PistonBlock && PlayerUtils.canInstantlyMineBlock(pistonState)) {
                PlayerInteractionUtils.updateBlockBreakingProgress(pos);
            }
            BlockState pistonUpState = world.getBlockState(pos2);
            if (pistonUpState.getBlock() instanceof PistonBlock && PlayerUtils.canInstantlyMineBlock(pistonUpState)) {
                PlayerInteractionUtils.updateBlockBreakingProgress(pos2);
            }
        }
        this.nextState = null;
        this.tickTotalCount = 0;
        this.ticksTotalMax = 100;
        this.ticksTimeoutMax = 45;
        this.tickWaitMax = 0;
        this.planItem = null;
        this.recycledItems.clear();
        this.executed = false;
        this.recycled = false;
        this.timeout = false;
        this.currentState = TaskState.WAIT_GAME_UPDATE;
        this.retry = false;
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
        if (!recycledItems.contains(pos)) {
            recycledItems.add(pos);
        }
    }

    public boolean isComplete() {
        return currentState == TaskState.COMPLETE || tickTotalCount >= ticksTotalMax;
    }

    public TaskState getCurrentState() {
        return currentState;
    }
}
