package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.Debug;
import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.*;
import com.google.common.collect.Queues;
import net.minecraft.block.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;
import static net.minecraft.block.Block.sideCoversSmallSquare;

public class Task {
    public final ClientWorld world;
    public final Block block;
    public final BlockPos pos;

    private TaskState currentState;
    private TaskState lastState;
    private @Nullable TaskState nextState;

    public final TaskSeekSchemeInfo[] taskSchemes;
    public @Nullable Direction direction;
    public @Nullable TaskSeekBlockInfo piston;
    public @Nullable TaskSeekBlockInfo redstoneTorch;
    public @Nullable TaskSeekBlockInfo slimeBlock;

    public final Queue<BlockPos> recycledQueue;
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

    public Task(ClientWorld world, Block block, BlockPos pos) {
        this.world = world;
        this.block = block;
        this.pos = pos;
        this.taskSchemes = TaskSeekSchemeTools.findAllPossible(pos, world);
        this.recycledQueue = Queues.newConcurrentLinkedQueue();
        this.init();
    }

    private void setWait(@Nullable TaskState nextState, int tickWaitMax) {
        this.nextState = nextState;
        this.tickWaitMax = Math.max(tickWaitMax, 1);
        this.currentState = TaskState.WAIT_CUSTOM;
    }

    private void setModifyLook(TaskSeekBlockInfo blockInfo) {
        if (blockInfo != null) {
            debug("修改视角");
            setModifyLook(blockInfo.facing);
            blockInfo.modify = true;
        }
    }

    private void setModifyLook(Direction facing) {
        PlayerLookManager.set(facing, this);
    }

    private void resetModifyLook() {
        if (PlayerLookManager.isModify()) {
            PlayerLookManager.reset();
        }
    }

    public void tick() {
        if (this.currentState == TaskState.COMPLETE) {
            return;
        }
        this.lastState = this.currentState; // 先将现有状态记录（debug输出）
        debug("开始");
        if (this.tickTotalCount >= this.ticksTotalMax) {
            this.currentState = TaskState.COMPLETE;
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
                    if (!recycledQueue.isEmpty()) {
                        currentState = TaskState.RECYCLED_ITEMS;
                        return;
                    }
                    if (retryCount < retryCountMax) {
                        retryCount++;
                        debug("任务物品回收已完成, 超时重试: %s", retryCount);
                        currentState = TaskState.INITIALIZE;
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
                break;
            }
            if (this.currentState.isExclusiveTick()) {
                if (this.lastState.isExclusiveTick()) {
                    if (this.lastState == TaskState.WAIT_GAME_UPDATE) {
                        continue;
                    }
                    break;
                } else {
                    continue;
                }
            }
            ++tickInternalCount;
        }
        debug("结束\r\n");
        ++tickTotalCount;
    }

    private void placeSlimeBlock() {
        if (slimeBlock == null) {
            this.currentState = TaskState.WAIT_GAME_UPDATE;
            return;
        }
        if (slimeBlock.isNeedModify() && !slimeBlock.modify) {
            setModifyLook(slimeBlock);
            return;
        }
        BlockPlacerUtils.placement(slimeBlock.pos, slimeBlock.facing, Items.SLIME_BLOCK);
        addRecycled(slimeBlock.pos);
        this.currentState = TaskState.WAIT_GAME_UPDATE;
        resetModifyLook();
    }

    private void placeRedstoneTorch() {
        if (redstoneTorch == null) {
            this.currentState = TaskState.FIND;
            return;
        }
        debug("红石火把");
        if (redstoneTorch.isNeedModify() && !redstoneTorch.modify) {
            setModifyLook(redstoneTorch);
            return;
        }
        BlockPlacerUtils.placement(redstoneTorch.pos, redstoneTorch.facing, Items.REDSTONE_TORCH);
        addRecycled(redstoneTorch.pos);
        setWait(TaskState.WAIT_GAME_UPDATE, Config.INSTANCE.taskShortWait ? 1 : 2);
        resetModifyLook();
    }

    private void placePiston() {
        if (piston == null) {
            this.currentState = TaskState.FIND;
            return;
        }
        debug("放置活塞");
        var placeBlockState = Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, piston.facing);
        if (BlockPlacerUtils.canPlace(world, piston.pos, placeBlockState)) {
            if (piston.isNeedModify() && !piston.modify) {
                setModifyLook(piston);
                return;
            }
            BlockPlacerUtils.placement(piston.pos, piston.facing, Items.PISTON);
            addRecycled(piston.pos);
            setWait(TaskState.WAIT_GAME_UPDATE, Config.INSTANCE.taskShortWait ? 1 : 3);
            resetModifyLook();
        } else {
            this.piston = null;
            this.currentState = TaskState.FIND;
        }
    }

    private void find() {
        if (this.piston == null || this.redstoneTorch == null || this.slimeBlock == null) {
            this.piston = null;
            this.redstoneTorch = null;
            this.slimeBlock = null;
            debug("查找方案");
            for (TaskSeekSchemeInfo taskSchemeInfo : taskSchemes) {
                final var direction = taskSchemeInfo.direction;
                final var piston = taskSchemeInfo.piston;
                final var redstoneTorch = taskSchemeInfo.redstoneTorch;
                final var slimeBlock = taskSchemeInfo.slimeBlock;
                if (!World.isValid(piston.pos) || !World.isValid(redstoneTorch.pos) || !World.isValid(slimeBlock.pos)) {
                    continue;
                }

                final var pistonState = world.getBlockState(piston.pos);
                final var pistonHeadState = world.getBlockState(piston.pos.offset(piston.facing));
                // 检查活塞位置
                if (!(pistonState.isReplaceable() && pistonHeadState.isReplaceable())) {
                    if (!(pistonState.getBlock() instanceof PistonBlock)) {
                        continue;
                    }
                }
                // 预检查, 避免无法放置导致失败
                final var redstoneTorchState = world.getBlockState(redstoneTorch.pos);
                if (!redstoneTorchState.isReplaceable()) {  // 如果该位置已存在方块
                    // 当前位置方块类型
                    if (!(redstoneTorchState.getBlock() instanceof RedstoneTorchBlock
                            || redstoneTorchState.getBlock() instanceof WallRedstoneTorchBlock
                    )) {
                        continue;
                    }
                }
                // 预检查, 避免无法放置导致失败
                if (!(world.getBlockState(slimeBlock.pos).isReplaceable() || sideCoversSmallSquare(world, slimeBlock.pos, slimeBlock.facing))) {
                    continue;
                }

                this.direction = direction;
                this.piston = piston;
                this.redstoneTorch = redstoneTorch;
                this.slimeBlock = slimeBlock;
                break;
            }
        }
        if (this.piston == null) {
            currentState = TaskState.FAIL;
            MessageUtils.setOverlayMessage(Text.literal(I18n.HANDLE_SEEK.getString().replace("%BlockPos%", pos.toShortString())));
        } else {
            this.currentState = TaskState.WAIT_GAME_UPDATE;
        }
    }

    private void recycledItems() {
        if (!recycledQueue.isEmpty()) {
            var blockPos = recycledQueue.peek();
            var blockState = world.getBlockState(blockPos);
            debug("任务物品正在回收: (%s) --> %s", blockPos.toShortString(), blockState.getBlock().getName().getString());
            var instant = world.getBlockState(blockPos).calcBlockBreakingDelta(player, world, blockPos) >= ClientPlayerInteractionManagerUtils.BREAKING_PROGRESS_MAX;
            if (!instant) {
                InventoryManagerUtils.autoSwitch(blockState);
            }
            ClientPlayerInteractionManagerUtils.updateBlockBreakingProgress(blockPos);
            if (blockState.isReplaceable()) {
                recycledQueue.remove(blockPos);
            }
            if (instant && !recycledQueue.isEmpty()) {
                recycledItems();
            }
        }
        if (recycledQueue.isEmpty()) {
            debug("任务物品回收已完成");
            if (retry) {
                currentState = TaskState.RETRY;
            } else {
                currentState = TaskState.COMPLETE;
            }
        }
    }

    private void execute() {
        if (executed || player == null || direction == null || piston == null || redstoneTorch == null || slimeBlock == null) {
            return;
        }
        if (!executeModify && direction.getAxis().isHorizontal()) {
            setModifyLook(direction.getOpposite());
            executeModify = true;
            return;
        } else {
            // 切换到工具
            if (world.getBlockState(piston.pos).calcBlockBreakingDelta(player, world, piston.pos) < 1F) {
                InventoryManagerUtils.autoSwitch(world.getBlockState(piston.pos));
                setWait(TaskState.EXECUTE, 1);
                return;
            }
            // 打掉附近红石火把
            BlockPos[] nearbyRedstoneTorch = TaskSeekSchemeTools.findPistonNearbyRedstoneTorch(piston.pos, world);
            for (BlockPos pos : nearbyRedstoneTorch) {
                if (world.getBlockState(pos).getBlock() instanceof RedstoneTorchBlock) {
                    ClientPlayerInteractionManagerUtils.updateBlockBreakingProgress(pos);
                }
            }
            if (world.getBlockState(redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock) {
                ClientPlayerInteractionManagerUtils.updateBlockBreakingProgress(redstoneTorch.pos);
            }
            ClientPlayerInteractionManagerUtils.updateBlockBreakingProgress(piston.pos);
            BlockPlacerUtils.placement(piston.pos, direction.getOpposite(), Items.PISTON);
            addRecycled(piston.pos);
            if (executeModify) {
                resetModifyLook();
            }
            executed = true;
        }
        setWait(TaskState.WAIT_GAME_UPDATE, 4);
    }

    private void waitCustom() {
        if (--this.tickWaitMax <= 0) {
            this.currentState = this.nextState == null ? TaskState.WAIT_GAME_UPDATE : this.nextState;
            this.tickWaitMax = 0;
            this.debug("等待已结束, 状态设置为: %s", this.currentState);
        } else {
            ++this.ticksTotalMax;
            ++this.ticksTimeoutMax;
            this.debug("剩余等待TICK: %s", tickWaitMax);
        }
    }

    private void updateStates() {
        if (this.piston != null && world.getBlockState(this.piston.pos).isOf(Blocks.MOVING_PISTON)) {
            this.debugUpdateStates("活塞正在移动");
            return;
        }
        if (!world.getBlockState(pos).isOf(block)) {
            this.currentState = TaskState.RECYCLED_ITEMS;
            this.debugUpdateStates("目标不存在");
            return;
        }
        if (!this.executed) {
            debugUpdateStates("任务未执行过");
            // 获取放置位置
            if (this.piston == null) {
                this.debugUpdateStates("活塞未获取,准备查找合适的位置");
                this.currentState = TaskState.FIND;
                return;
            }
            if (this.redstoneTorch == null) {
                this.debugUpdateStates("红石火把未获取,准备查找合适的位置");
                this.currentState = TaskState.FIND;
                return;
            }
            if (this.slimeBlock == null) {
                this.debugUpdateStates("红石火把底座未获取,准备查找合适的位置");
                this.currentState = TaskState.FIND;
                return;
            }
            // 放活塞
            if (world.getBlockState(this.piston.pos).isReplaceable()) {
                this.debugUpdateStates("[%s] [%s] 活塞未放置且该位置可放置物品,设置放置状态", this.piston.pos.toShortString(), this.piston.facing);
                this.currentState = TaskState.PLACE_PISTON;
                return;
            } else if (!(world.getBlockState(this.piston.pos).getBlock() instanceof PistonBlock)) {
                this.currentState = TaskState.FIND;
                return;
            }
            // 先放底座
            if (world.getBlockState(this.slimeBlock.pos).isReplaceable()) {
                this.debugUpdateStates("[%s] [%s] 底座未放置且该位置可放置物品,设置放置状态", this.slimeBlock.pos.toShortString(), this.slimeBlock.facing);
                this.currentState = TaskState.PLACE_SLIME_BLOCK;
                return;
            } else if (!Block.sideCoversSmallSquare(world, slimeBlock.pos, slimeBlock.facing)) {
                this.currentState = TaskState.FIND;
                return;
            }
            // 放红石火把
            if (world.getBlockState(redstoneTorch.pos).isReplaceable()) {
                this.debugUpdateStates("[%s] [%s] 红石火把未放置且该位置可放置物品,设置放置状态", this.redstoneTorch.pos.toShortString(), this.redstoneTorch.facing);
                this.currentState = TaskState.PLACE_REDSTONE_TORCH;
                return;
            } else if (!(world.getBlockState(redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock
                    || world.getBlockState(redstoneTorch.pos).getBlock() instanceof WallRedstoneTorchBlock)) {
                this.currentState = TaskState.FIND;
                return;
            }
            if (world.getBlockState(this.piston.pos).getBlock() instanceof PistonBlock) {
                if (world.getBlockState(this.piston.pos).contains(PistonBlock.EXTENDED)) {
                    if (world.getBlockState(this.piston.pos).get(PistonBlock.EXTENDED)) {
                        this.currentState = TaskState.EXECUTE;
                    }
                }
            }
        }
    }

    private void init() {
        this.nextState = null;
        this.tickTotalCount = 0;
        this.ticksTotalMax = 100;
        this.ticksTimeoutMax = 45;
        this.tickWaitMax = 0;
        this.direction = null;
        this.piston = null;
        this.redstoneTorch = null;
        this.slimeBlock = null;
        this.recycledQueue.clear();
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
        if (!recycledQueue.contains(pos)) {
            recycledQueue.add(pos);
        }
    }

    public boolean isComplete() {
        return currentState == TaskState.COMPLETE || tickTotalCount >= ticksTotalMax;
    }

    public TaskState getCurrentState() {
        return currentState;
    }
}
