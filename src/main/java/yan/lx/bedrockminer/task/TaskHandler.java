package yan.lx.bedrockminer.task;

import com.google.common.collect.Queues;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import yan.lx.bedrockminer.Debug;
import yan.lx.bedrockminer.LanguageText;
import yan.lx.bedrockminer.config.Config;
import yan.lx.bedrockminer.utils.*;

import java.util.Queue;

import static net.minecraft.block.Block.sideCoversSmallSquare;
import static yan.lx.bedrockminer.BedrockMiner.player;

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
    private int ticksTotalMax;
    private int ticksTimeoutMax;
    private int tickWaitMax;
    public int retryCount;
    public int retryCountMax;
    public boolean executed;
    public boolean recycled;
    public int recycledCount;
    public boolean timeout;

    public TaskHandler(ClientWorld world, Block block, BlockPos pos) {
        this.debug("[构造函数] 开始\r\n");
        this.world = world;
        this.block = block;
        this.pos = pos;
        this.state = TaskState.INITIALIZE;
        this.taskSchemes = TaskSeekSchemeTools.findAllPossible(pos, world);
        this.recycledQueue = Queues.newConcurrentLinkedQueue();
        this.retryCount = 0;
        this.retryCountMax = 1;
        this.init();
        this.debug("[构造函数] 结束\r\n");
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
        TaskPlayerLookManager.reset();
    }

    public void tick() {
        this.tick(false);
    }

    private void tickInternal() {
        this.tick(true);
    }

    private void tick(boolean internalCallbacks) {
        if (internalCallbacks) {
            debug("内部调用开始");
        } else {
            debug("开始");
        }
        if (this.state == TaskState.COMPLETE) {
            return;
        }
        if (this.ticks > this.ticksTotalMax) {
            this.state = TaskState.COMPLETE;
        }
        if (!this.timeout && this.ticks > this.ticksTimeoutMax) {
            this.timeout = true;
            this.state = TaskState.TIMEOUT;
        }

        switch (this.state) {
            case INITIALIZE -> this.init();
            case WAIT_GAME_UPDATE -> this.updateStates();
            case WAIT_CUSTOM -> this.waitCustom();
            case FIND_PISTON -> this.findPiston();
            case FIND_REDSTONE_TORCH -> this.findRedstoneTorch();
            case FIND_SLIME_BLOCK -> this.findSlimeBlock();
            case PLACE_PISTON -> this.placePiston();
            case PLACE_REDSTONE_TORCH -> this.placeRedstoneTorch();
            case PLACE_SLIME_BLOCK -> this.placeSlimeBlock();
            case EXECUTE -> this.execute();
            case TIMEOUT -> this.timeout();
            case FAIL -> this.fail();
            case RECYCLED_ITEMS -> this.recycledItems();
            case COMPLETE -> complete();
        }

        if (internalCallbacks) {
            debug("内部调用结束");
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
        BlockPlacerUtils.placement(slimeBlock.pos, slimeBlock.facing, Items.SLIME_BLOCK);
        addRecycled(slimeBlock.pos);
        setWait(TaskState.WAIT_GAME_UPDATE, 1);
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
        BlockPlacerUtils.placement(redstoneTorch.pos, redstoneTorch.facing, Items.REDSTONE_TORCH);
        addRecycled(redstoneTorch.pos);
        setWait(TaskState.WAIT_GAME_UPDATE, 3);
        resetModifyLook();
    }

    private void placePiston() {
        if (piston == null) {
            findPiston();
        }
        if (piston.isNeedModify() && !piston.modify) {
            setModifyLook(piston);
            return;
        }
        BlockPlacerUtils.placement(piston.pos, piston.facing, Items.PISTON);
        addRecycled(piston.pos);
        setWait(TaskState.WAIT_GAME_UPDATE, 1);
        resetModifyLook();
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
                            if (!CheckingEnvironmentUtils.canPlace(slimeBlock.pos, Blocks.SLIME_BLOCK, slimeBlock.facing)) {
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
            MessageUtils.setOverlayMessage(Text.literal(LanguageText.HANDLE_SEEK.getString().replace("%BlockPos%", pos.toShortString())));
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
                        if (!CheckingEnvironmentUtils.canPlace(slimeBlock.pos, Blocks.SLIME_BLOCK, slimeBlock.facing)) {
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
            MessageUtils.setOverlayMessage(Text.literal(LanguageText.HANDLE_SEEK.getString().replace("%BlockPos%", pos.toShortString())));
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
                    if (!CheckingEnvironmentUtils.canPlace(slimeBlock.pos, Blocks.SLIME_BLOCK, slimeBlock.facing)) {
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
            MessageUtils.setOverlayMessage(Text.literal(LanguageText.HANDLE_SEEK.getString().replace("%BlockPos%", pos.toShortString())));
        } else {
            state = TaskState.WAIT_GAME_UPDATE;
        }
    }

    private void complete() {
        debug("任务已完成");
    }

    private void recycledItems() {
        if (!recycled) recycled = true;
        var player = MinecraftClient.getInstance().player;
        if (!recycledQueue.isEmpty() && player != null) {
            var blockPos = recycledQueue.peek();
            BlockBreakerUtils.updateBlockBreakingProgress(blockPos);
            var blockState = world.getBlockState(blockPos);
            if (blockState.calcBlockBreakingDelta(player, world, blockPos) < 1F) {
                InventoryManagerUtils.autoSwitch(blockState);
                return;
            }
            if (blockState.isReplaceable()) {
                recycledQueue.remove(blockPos);
            }
            return;
        }
        state = TaskState.COMPLETE;
    }

    private void fail() {
        state = TaskState.RECYCLED_ITEMS;
    }

    private void timeout() {
        state = TaskState.FAIL;
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
                setWait(TaskState.EXECUTE, 3);
                return;
            }
            // 打掉附近红石火把
            BlockPos[] nearbyRedstoneTorch = TaskSeekSchemeTools.findPistonNearbyRedstoneTorch(piston.pos, world);
            for (BlockPos pos : nearbyRedstoneTorch) {
                if (world.getBlockState(pos).getBlock() instanceof RedstoneTorchBlock) {
                    BlockBreakerUtils.attackBlock(pos);
                }
            }
            if (world.getBlockState(redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock) {
                BlockBreakerUtils.attackBlock(redstoneTorch.pos);
            }
            BlockBreakerUtils.attackBlock(piston.pos);
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
            this.state = this.nextState == null ? TaskState.WAIT_GAME_UPDATE : this.nextState;
            this.tickWaitMax = 0;
            debug("等待已结束, 状态设置为: %s", this.state);
        } else {
            ++this.ticksTotalMax;
            ++this.ticksTimeoutMax;
            debug("剩余等待TICK: %s", tickWaitMax);
        }
    }

    private void updateStates() {
        if (this.piston != null && world.getBlockState(this.piston.pos).isOf(Blocks.MOVING_PISTON)) {
            debugUpdateStates("活塞正在移动");
            return;
        }
        if (!world.getBlockState(pos).isOf(block)) {
            state = TaskState.RECYCLED_ITEMS;
            debugUpdateStates("目标不存在");
            return;
        }
        if (!this.executed) {
            debugUpdateStates("任务未执行过");

            // 获取放置位置
            if (this.piston == null) {
                this.debugUpdateStates("活塞未获取,准备查找合适的位置");
                this.state = TaskState.FIND_PISTON;
                this.tickInternal();    // 查找算法,不需要独占TICK执行
                return;
            }

            if (this.redstoneTorch == null) {
                this.debugUpdateStates("红石火把未获取,准备查找合适的位置");
                this.state = TaskState.FIND_REDSTONE_TORCH;
                this.tickInternal();    // 查找算法,不需要独占TICK执行
                return;
            }
            if (this.slimeBlock == null) {
                this.debugUpdateStates("红石火把底座未获取,准备查找合适的位置");
                this.state = TaskState.FIND_SLIME_BLOCK;
                this.tickInternal();    // 查找算法,不需要独占TICK执行
                return;
            }

            if (world.getBlockState(this.piston.pos).isReplaceable()) {
                this.debugUpdateStates("[%s] 活塞未放置且该位置可放置物品,设置放置状态", this.piston.pos.toShortString());
                this.state = TaskState.PLACE_PISTON;
                return;
            } else if (!(world.getBlockState(this.piston.pos).getBlock() instanceof PistonBlock)) {
                this.findPiston();
                return;
            }


            if (world.getBlockState(this.slimeBlock.pos).isReplaceable()) {
                this.state = TaskState.PLACE_SLIME_BLOCK;
                return;
            } else if (!Block.sideCoversSmallSquare(world, slimeBlock.pos, slimeBlock.facing)) {
                this.findRedstoneTorch();
                return;
            }

            if (world.getBlockState(redstoneTorch.pos).isReplaceable()) {
                state = TaskState.PLACE_REDSTONE_TORCH;
                return;
            } else if (!(world.getBlockState(redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock)) {
                this.findRedstoneTorch();
                return;
            }

            if (world.getBlockState(this.piston.pos).getBlock() instanceof PistonBlock) {
                if (world.getBlockState(this.piston.pos).contains(PistonBlock.EXTENDED)) {
                    if (world.getBlockState(this.piston.pos).get(PistonBlock.EXTENDED)) {
                        this.state = TaskState.EXECUTE;
                    }
                }
            }
        }
    }

    private void init() {
        this.state = TaskState.WAIT_GAME_UPDATE;
        this.nextState = null;
        this.ticks = 0;
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
        this.recycledCount = 0;
        this.timeout = false;

        this.tickInternal();
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
        return state == TaskState.COMPLETE;
    }
}
