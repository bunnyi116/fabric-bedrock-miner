package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.Debug;
import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.ClientPlayerInteractionManagerUtils;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.PlayerInventoryManagerUtils;
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

import static com.github.bunnyi116.bedrockminer.Mod.player;
import static net.minecraft.block.Block.sideCoversSmallSquare;

public class TaskHandler {
    public final ClientWorld world;
    public final Block block;
    public final BlockPos pos;

    private TaskState state;
    private @Nullable TaskState nextState;

    public final TaskSeekSchemeInfo[] taskSchemes;
    public @Nullable Direction direction;
    public @Nullable TaskSeekBlockInfo piston;
    public @Nullable TaskSeekBlockInfo redstoneTorch;
    public @Nullable TaskSeekBlockInfo slimeBlock;

    public final Queue<BlockPos> recycledQueue;
    public boolean executeModify;
    private int ticks;
    private int ticksPrivateInvoke;
    private int ticksPrivateInvokeMax;
    private int ticksTotalMax;
    private int ticksTimeoutMax;
    private int tickWaitMax;
    public int retryCount = 0;
    public int retryCountMax = 1;
    public boolean executed;
    public boolean recycled;
    public boolean timeout;

    public TaskHandler(ClientWorld world, Block block, BlockPos pos) {
        this.world = world;
        this.block = block;
        this.pos = pos;
        this.taskSchemes = TaskSeekSchemeTools.findAllPossible(pos, world);
        this.recycledQueue = Queues.newConcurrentLinkedQueue();
        this.init(false);
    }

    private void setWait(@Nullable TaskState nextState, int tickWaitMax) {
        this.nextState = nextState;
        this.tickWaitMax = Math.max(tickWaitMax, 1);
        this.state = TaskState.WAIT_CUSTOM;
    }

    private void setModifyLook(TaskSeekBlockInfo blockInfo) {
        if (blockInfo != null) {
            setModifyLook(blockInfo.facing);
            blockInfo.modify = true;
        }
    }

    private void setModifyLook(Direction facing) {
        TaskPlayerLookManager.set(facing, this);
    }

    private void resetModifyLook() {
        if (TaskPlayerLookManager.isModify()) {
            TaskPlayerLookManager.reset();
        }
    }

    public void tick() {
        this.tick(false);
    }

    private void tick(boolean privateInvoke) {
        if (this.state == TaskState.COMPLETE) {
            return;
        }
        if (privateInvoke && this.ticksPrivateInvoke++ >= ticksPrivateInvokeMax)   // 暂时不知道什么情况，临时防止无限死循环办法
        {
            this.ticksPrivateInvoke = 0;
            return;
        }
        if (privateInvoke) {
            debug("内部调用开始");
        } else {
            debug("开始");
        }
        if (this.ticks >= this.ticksTotalMax) {
            this.state = TaskState.COMPLETE;
        }
        if (!this.timeout && this.ticks >= this.ticksTimeoutMax) {
            this.timeout = true;
            this.state = TaskState.TIMEOUT;
        }
        switch (this.state) {
            case INITIALIZE -> this.init(true);
            case WAIT_GAME_UPDATE -> this.updateStates();
            case WAIT_CUSTOM -> this.waitCustom();
            case FIND_PISTON -> this.findPiston();
            case FIND_REDSTONE_TORCH -> this.findRedstoneTorch();
            case FIND_SLIME_BLOCK -> this.findSlimeBlock();
            case PLACE_PISTON -> this.placePiston();
            case PLACE_REDSTONE_TORCH -> this.placeRedstoneTorch();
            case PLACE_SLIME_BLOCK -> this.placeSlimeBlock();
            case EXECUTE -> this.execute();
            case TIMEOUT -> {
                debug("任务已超时");
                state = TaskState.RECYCLED_ITEMS;
                tick(true);
            }
            case FAIL -> {
                debug("任务已失败");
                state = TaskState.RECYCLED_ITEMS;
                tick(true);
            }
            case RECYCLED_ITEMS -> this.recycledItems();
            case COMPLETE -> debug("任务已完成");
        }
        if (privateInvoke) {
            debug("内部调用结束");
            ++ticksPrivateInvokeMax;
        } else {
            debug("结束\r\n");
            ++ticks;
        }
    }

    private void placeSlimeBlock() {
        if (slimeBlock == null) {
            findPiston();
            return;
        }
        if (slimeBlock.isNeedModify() && !slimeBlock.modify) {
            setModifyLook(slimeBlock);
            return;
        }
        ClientPlayerInteractionManagerUtils.placement(slimeBlock.pos, slimeBlock.facing, Items.SLIME_BLOCK);
        addRecycled(slimeBlock.pos);
        this.state = TaskState.WAIT_GAME_UPDATE;
        resetModifyLook();
    }

    private void placeRedstoneTorch() {
        if (redstoneTorch == null) {
            findPiston();
        }
        if (redstoneTorch.isNeedModify() && !redstoneTorch.modify) {
            setModifyLook(redstoneTorch);
            return;
        }
        ClientPlayerInteractionManagerUtils.placement(redstoneTorch.pos, redstoneTorch.facing, Items.REDSTONE_TORCH);
        addRecycled(redstoneTorch.pos);
        setWait(TaskState.WAIT_GAME_UPDATE, Config.INSTANCE.taskShortWait ? 1 : 3); // 3是激活活塞伸出的时间
        resetModifyLook();
    }

    private void placePiston() {
        if (piston == null) {
            findPiston();
        }
        BlockState defaultState = Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, piston.facing);
        if (ClientPlayerInteractionManagerUtils.canPlace(piston.pos, defaultState)) {
            if (piston.isNeedModify() && !piston.modify) {
                setModifyLook(piston);
                return;
            }
            ClientPlayerInteractionManagerUtils.placement(piston.pos, piston.facing, Items.PISTON);
            addRecycled(piston.pos);
            setWait(TaskState.WAIT_GAME_UPDATE, 1);
            resetModifyLook();
        } else {
            this.piston = null;
            this.state = TaskState.FIND_PISTON;
            this.tick(true);
        }
    }

    private void findSlimeBlock() {
        if (slimeBlock == null || !(world.getBlockState(slimeBlock.pos).isReplaceable()
                || sideCoversSmallSquare(world, slimeBlock.pos, slimeBlock.facing))) {
            for (TaskSeekSchemeInfo taskSchemeInfo : taskSchemes) {
                if (taskSchemeInfo.piston.equals(piston)) {
                    TaskSeekBlockInfo piston = taskSchemeInfo.piston;
                    if (taskSchemeInfo.redstoneTorch.equals(redstoneTorch)) {
                        TaskSeekBlockInfo redstoneTorch = taskSchemeInfo.redstoneTorch;
                        TaskSeekBlockInfo slimeBlock = taskSchemeInfo.slimeBlock;
                        if (World.isValid(piston.pos) && World.isValid(redstoneTorch.pos) && World.isValid(slimeBlock.pos)) {
                            BlockState pistonState = world.getBlockState(piston.pos);
                            BlockState pistonHeadState = world.getBlockState(piston.pos.offset(piston.facing));
                            // 检查活塞位置
                            if (!(pistonState.isReplaceable() && pistonHeadState.isReplaceable())) {
                                if (!(pistonState.getBlock() instanceof PistonBlock)) {
                                    continue;
                                }
                            }
                            // 预检查, 避免无法放置导致失败
                            BlockState redstoneTorchState = world.getBlockState(redstoneTorch.pos);
                            if (!redstoneTorchState.isReplaceable()) {
                                if (!(redstoneTorchState.getBlock() instanceof RedstoneTorchBlock)) {
                                    continue;
                                }
                            }
                            // 预检查, 避免无法放置导致失败
                            if (!(world.getBlockState(slimeBlock.pos).isReplaceable() || sideCoversSmallSquare(world, slimeBlock.pos, slimeBlock.facing))) {
                                continue;
                            }
                            if (!ClientPlayerInteractionManagerUtils.canPlace(slimeBlock.pos, Blocks.SLIME_BLOCK.getDefaultState())) {
                                continue;
                            }
                            if (slimeBlock.facing.getAxis().isHorizontal()) {
                                if (!Config.INSTANCE.horizontal) {
                                    continue;
                                }
                            }
                            if (slimeBlock.facing.getAxis().isVertical()) {
                                if (!Config.INSTANCE.vertical) {
                                    continue;
                                }
                            }
                            this.slimeBlock = slimeBlock;
                            break;
                        }
                    }
                }
            }
        }
        if (this.slimeBlock == null) {
//            state = TaskState.FAIL;
            MessageUtils.setOverlayMessage(Text.literal(I18n.HANDLE_SEEK.getString().replace("%BlockPos%", pos.toShortString())));
        } else {
            state = TaskState.WAIT_GAME_UPDATE;
        }
    }

    private void findRedstoneTorch() {
        if (redstoneTorch == null || !(world.getBlockState(redstoneTorch.pos).isReplaceable() || sideCoversSmallSquare(world, redstoneTorch.pos, redstoneTorch.facing))) {
            for (TaskSeekSchemeInfo taskSchemeInfo : taskSchemes) {
                if (taskSchemeInfo.piston.equals(piston)) {
                    TaskSeekBlockInfo piston = taskSchemeInfo.piston;
                    TaskSeekBlockInfo redstoneTorch = taskSchemeInfo.redstoneTorch;
                    TaskSeekBlockInfo slimeBlock = taskSchemeInfo.slimeBlock;
                    if (World.isValid(piston.pos) && World.isValid(redstoneTorch.pos) && World.isValid(slimeBlock.pos)) {
                        BlockState pistonState = world.getBlockState(piston.pos);
                        BlockState pistonHeadState = world.getBlockState(piston.pos.offset(piston.facing));
                        // 检查活塞位置
                        if (!(pistonState.isReplaceable() && pistonHeadState.isReplaceable())) {
                            if (!(pistonState.getBlock() instanceof PistonBlock)) {
                                continue;
                            }
                        }
                        // 预检查, 避免无法放置导致失败
                        BlockState redstoneTorchState = world.getBlockState(redstoneTorch.pos);
                        if (!redstoneTorchState.isReplaceable()) {
                            if (!(redstoneTorchState.getBlock() instanceof RedstoneTorchBlock)) {
                                continue;
                            }
                        }
                        if (!(world.getBlockState(slimeBlock.pos).isReplaceable() || sideCoversSmallSquare(world, slimeBlock.pos, slimeBlock.facing))) {
                            continue;
                        }
                        if (!ClientPlayerInteractionManagerUtils.canPlace(slimeBlock.pos, Blocks.SLIME_BLOCK.getDefaultState())) {
                            continue;
                        }
                        if (redstoneTorch.facing.getAxis().isHorizontal()) {
                            if (!Config.INSTANCE.horizontal) {
                                continue;
                            }
                        }
                        if (redstoneTorch.facing.getAxis().isVertical()) {
                            if (!Config.INSTANCE.vertical) {
                                continue;
                            }
                        }
                        this.redstoneTorch = redstoneTorch;
                        this.slimeBlock = null;
                        break;
                    }
                }
            }
        }
        if (this.redstoneTorch == null) {
//            state = TaskState.FAIL;
            MessageUtils.setOverlayMessage(Text.literal(I18n.HANDLE_SEEK.getString().replace("%BlockPos%", pos.toShortString())));
        } else {
            state = TaskState.WAIT_GAME_UPDATE;
        }
    }

    private void findPiston() {
        if (this.piston == null) {
            for (TaskSeekSchemeInfo taskSchemeInfo : taskSchemes) {
                Direction direction = taskSchemeInfo.direction;
                TaskSeekBlockInfo piston = taskSchemeInfo.piston;
                TaskSeekBlockInfo redstoneTorch = taskSchemeInfo.redstoneTorch;
                TaskSeekBlockInfo slimeBlock = taskSchemeInfo.slimeBlock;
                if (direction.getAxis().isHorizontal()) {
                    if (!Config.INSTANCE.horizontal) {
                        continue;
                    }
                }
                if (direction.getAxis().isVertical()) {
                    if (!Config.INSTANCE.vertical) {
                        continue;
                    }
                }
                if (World.isValid(piston.pos) && World.isValid(redstoneTorch.pos) && World.isValid(slimeBlock.pos)) {
                    BlockState pistonState = world.getBlockState(piston.pos);
                    BlockState pistonHeadState = world.getBlockState(piston.pos.offset(piston.facing));
                    // 检查活塞位置
                    if (!(pistonState.isReplaceable() && pistonHeadState.isReplaceable())) {
                        if (!(pistonState.getBlock() instanceof PistonBlock)) {
                            continue;
                        }
                    }
                    // 预检查, 避免无法放置导致失败
                    BlockState redstoneTorchState = world.getBlockState(redstoneTorch.pos);
                    if (!redstoneTorchState.isReplaceable()) {
                        if (!(redstoneTorchState.getBlock() instanceof RedstoneTorchBlock)) {
                            continue;
                        }
                    }
                    // 预检查, 避免无法放置导致失败
                    if (!(world.getBlockState(slimeBlock.pos).isReplaceable() || sideCoversSmallSquare(world, slimeBlock.pos, slimeBlock.facing))) {
                        continue;
                    }
                    if (!ClientPlayerInteractionManagerUtils.canPlace(slimeBlock.pos, Blocks.SLIME_BLOCK.getDefaultState())) {
                        continue;
                    }
                    if (piston.facing.getAxis().isHorizontal()) {
                        if (!Config.INSTANCE.horizontal) {
                            continue;
                        }
                    }
                    if (piston.facing.getAxis().isVertical()) {
                        if (!Config.INSTANCE.vertical) {
                            continue;
                        }
                    }
                    this.direction = direction;
                    this.piston = piston;
                    this.redstoneTorch = null;
                    this.slimeBlock = null;
                    break;
                }
            }
        }
        if (this.piston == null) {
//            state = TaskState.FAIL;
            MessageUtils.setOverlayMessage(Text.literal(I18n.HANDLE_SEEK.getString().replace("%BlockPos%", pos.toShortString())));
        } else {
            if (this.redstoneTorch == null) {
                this.findRedstoneTorch();
            }
            this.state = TaskState.WAIT_GAME_UPDATE;
            this.tick(true);
        }
    }

    private void recycledItems() {
        if (!recycledQueue.isEmpty()) {
            var blockPos = recycledQueue.peek();
            var blockState = world.getBlockState(blockPos);
            debug("任务物品正在回收: (%s) --> %s", blockPos.toShortString(), blockState.getBlock().getName().getString());
            PlayerInventoryManagerUtils.autoSwitch(blockState);
            ClientPlayerInteractionManagerUtils.updateBlockBreakingProgress(blockPos);
            if (blockState.isReplaceable()) {
                recycledQueue.remove(blockPos);
            }
            return;
        }
        if (timeout && retryCount < retryCountMax) {
            retryCount++;
            debug("任务物品回收已完成, 超时重试: %s", retryCount);
            state = TaskState.INITIALIZE;
            this.tick(true);
        } else {
            debug("任务物品回收已完成");
            state = TaskState.COMPLETE;
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
                PlayerInventoryManagerUtils.autoSwitch(world.getBlockState(piston.pos));
                setWait(TaskState.EXECUTE, Config.INSTANCE.taskShortWait ? 1 : 3);
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
            ClientPlayerInteractionManagerUtils.placement(piston.pos, direction.getOpposite(), Items.PISTON);
            addRecycled(piston.pos);
            if (executeModify) {
                resetModifyLook();
            }
            executed = true;
        }
        setWait(TaskState.WAIT_GAME_UPDATE, Config.INSTANCE.taskShortWait ? 1 : 3);
    }

    private void waitCustom() {
        if (--this.tickWaitMax <= 0) {
            this.state = this.nextState == null ? TaskState.WAIT_GAME_UPDATE : this.nextState;
            this.tickWaitMax = 0;
            this.debug("等待已结束, 状态设置为: %s", this.state);
            this.tick(true);
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
            this.state = TaskState.RECYCLED_ITEMS;
            this.debugUpdateStates("目标不存在");
            this.tick(true);
            return;
        }
        if (!this.executed) {
            debugUpdateStates("任务未执行过");
            // 获取放置位置
            if (this.piston == null) {
                this.debugUpdateStates("活塞未获取,准备查找合适的位置");
                this.state = TaskState.FIND_PISTON;
                this.tick(true);
                return;
            }
            if (this.redstoneTorch == null) {
                this.debugUpdateStates("红石火把未获取,准备查找合适的位置");
                this.state = TaskState.FIND_REDSTONE_TORCH;
                this.tick(true);
                return;
            }
            if (this.slimeBlock == null) {
                this.debugUpdateStates("红石火把底座未获取,准备查找合适的位置");
                this.state = TaskState.FIND_SLIME_BLOCK;
                this.tick(true);
                return;
            }

            if (world.getBlockState(this.piston.pos).isReplaceable()) {
                this.debugUpdateStates("[%s] 活塞未放置且该位置可放置物品,设置放置状态", this.piston.pos.toShortString());
                this.state = TaskState.PLACE_PISTON;
                this.tick(true);
                return;
            } else if (!(world.getBlockState(this.piston.pos).getBlock() instanceof PistonBlock)) {
                this.state = TaskState.FIND_PISTON;
                this.tick(true);
                return;
            }

            if (world.getBlockState(this.slimeBlock.pos).isReplaceable()) {
                this.state = TaskState.PLACE_SLIME_BLOCK;
                this.tick(true);
                return;
            } else if (!Block.sideCoversSmallSquare(world, slimeBlock.pos, slimeBlock.facing)) {
                this.state = TaskState.FIND_REDSTONE_TORCH;
                this.tick(true);
                return;
            }

            if (world.getBlockState(redstoneTorch.pos).isReplaceable()) {
                this.state = TaskState.PLACE_REDSTONE_TORCH;
                this.tick(true);
                return;
            } else if (!(world.getBlockState(redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock
                    || world.getBlockState(redstoneTorch.pos).getBlock() instanceof WallRedstoneTorchBlock)) {
                this.state = TaskState.FIND_REDSTONE_TORCH;
                this.tick(true);
                return;
            }

            if (world.getBlockState(this.piston.pos).getBlock() instanceof PistonBlock) {
                if (world.getBlockState(this.piston.pos).contains(PistonBlock.EXTENDED)) {
                    if (world.getBlockState(this.piston.pos).get(PistonBlock.EXTENDED)) {
                        this.state = TaskState.EXECUTE;
                        this.tick(true);
                    }
                }
            }
        }
    }

    private void init(boolean reset) {
        this.nextState = null;
        this.ticks = 0;
        this.ticksPrivateInvokeMax = 1;
        this.ticksTotalMax = 100;
        this.ticksTimeoutMax = 30;
        this.tickWaitMax = 0;
        this.direction = null;
        this.piston = null;
        this.redstoneTorch = null;
        this.slimeBlock = null;
        this.recycledQueue.clear();
        this.executed = false;
        this.recycled = false;
        this.timeout = false;
        this.state = TaskState.WAIT_GAME_UPDATE;
        this.tick(true);
    }

    private void debug(String var1, Object... var2) {
        Debug.write("[{}] [{}] [{}] {}", retryCount, ticks, state, String.format(var1, var2));
    }

    private void debugUpdateStates(String var1, Object... var2) {
        Debug.write("[{}] [{}] [{}] [状态更新] {}", retryCount, ticks, state, String.format(var1, var2));
    }

    private void addRecycled(BlockPos pos) {
        if (!recycledQueue.contains(pos)) {
            recycledQueue.add(pos);
        }
    }

    public boolean isComplete() {
        return state == TaskState.COMPLETE || ticks >= ticksTotalMax;
    }

    public TaskState getState() {
        return state;
    }
}
