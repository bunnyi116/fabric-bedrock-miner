package com.github.bunnyi116.bedrockminer.task;


import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.Debug;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.task.block.TaskTargetBlock;
import com.github.bunnyi116.bedrockminer.task.block.scheme.TaskSchemeBaseBlock;
import com.github.bunnyi116.bedrockminer.task.block.scheme.TaskSchemeLeverBlock;
import com.github.bunnyi116.bedrockminer.task.block.scheme.TaskSchemePistonBlock;
import com.github.bunnyi116.bedrockminer.task.block.scheme.TaskSchemeRedstoneTorchBlock;
import com.github.bunnyi116.bedrockminer.util.BlockPlacerUtils;
import com.github.bunnyi116.bedrockminer.util.ClientPlayerInteractionManagerUtils;
import com.google.common.collect.Queues;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Queue;

public class Task {
    public final static Direction[] DEFAULT_PISTON_DIRECTIONS = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    public final static Direction[] DEFAULT_PISTON_FACINGS = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public final static Direction[] DEFAULT_REDSTONE_TORCH_DIRECTIONS = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    public final static Direction[] DEFAULT_REDSTONE_TORCH_FACINGS = new Direction[]{Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public final static Direction[] DEFAULT_LEVER_DIRECTIONS = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    public final static Direction[] DEFAULT_LEVER_FACINGS = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public final TaskTargetBlock target;
    public final TaskMode mode;
    public final int tickMax;
    public final int retryMax;
    public final int waitMax;
    public final Queue<BlockPos> blockRecyclingQueue;
    public TaskState state;
    public @Nullable TaskState nextState;
    public int tickCount;
    public int retryCount;
    public int waitCount, waitInternalMax;
    public boolean fail;
    public @Nullable TaskSchemePistonBlock piston;
    public @Nullable TaskSchemeLeverBlock lever;
    public @Nullable TaskSchemeRedstoneTorchBlock redstoneTorch;
    public @Nullable TaskSchemeBaseBlock baseBlock;

    public Task(TaskTargetBlock target) {
        this.target = target;
        this.mode = Config.INSTANCE.taskMode;
        this.tickMax = Config.INSTANCE.tickMax;
        this.retryMax = Config.INSTANCE.retryMax;
        this.waitMax = Config.INSTANCE.waitMax;
        this.blockRecyclingQueue = Queues.newConcurrentLinkedQueue();
        this.init(true);
    }

    public Task(ClientWorld world, BlockPos pos, Block block) {
        this(new TaskTargetBlock(world, pos, block));
    }

    public boolean isComplete() {
        return this.state == TaskState.COMPLETE;
    }

    private void init(boolean constructor) {
        if (constructor) {
            this.retryCount = 0;
            this.state = TaskState.WAIT_GAME_UPDATE;
        } else {
            this.state = TaskState.FIND_PLACE;
        }
        this.nextState = null;
        this.tickCount = 0;
    }

    // 当前方法本身也占用一个TICK
    private void waitGameUpdate() {
        if (this.piston != null && this.piston.isOf(Blocks.MOVING_PISTON)) {
            this.debug("活塞正在移动");
            return;
        }
        if (this.target.isReplaceable()) {
            this.debug("目标方块已不存在");
            this.processingState(TaskState.COMPLETE);
            return;
        }
        if (this.piston == null) {
            this.processingState(TaskState.FIND_PLACE);
            return;
        }
        if (this.mode == TaskMode.REDSTONE_TORCH && this.redstoneTorch == null) {
            this.processingState(TaskState.FIND_PLACE);
            return;
        }
        if (this.mode == TaskMode.LEVER && this.lever == null) {
            this.processingState(TaskState.FIND_PLACE);
            return;
        }

    }

    private void setWaitCustomUpdate(@Nullable TaskState nextState, int waitInternalMax) {
        this.nextState = nextState;
        this.waitInternalMax = Math.max(waitInternalMax, 1);
    }

    // 当前方法本身也占用一个TICK
    private void waitCustomUpdate() {
        if (++this.waitCount >= this.waitMax + this.waitInternalMax) {
            this.waitInternalMax = 0;
            this.processingState(Objects.requireNonNullElse(this.nextState, TaskState.WAIT_GAME_UPDATE));
        }
    }

    private void findPlaceRedstoneTorch() {
        if (this.piston == null || this.redstoneTorch == null || this.baseBlock == null) {
            this.piston = null;
            this.redstoneTorch = null;
            this.baseBlock = null;
            // 查找活塞
            for (Direction pistonDirection : DEFAULT_PISTON_DIRECTIONS) {
                final BlockPos pistonPos = target.offset(pistonDirection);
                for (Direction pistonFacing : DEFAULT_PISTON_FACINGS) {
                    final BlockPos pistonHeadPos = pistonPos.offset(pistonFacing);
                    if (pistonHeadPos.equals(target.pos)) continue;    // 活塞头在目标方块位置
                    final TaskSchemePistonBlock piston = new TaskSchemePistonBlock(target.world, pistonPos, pistonDirection, pistonFacing);
                    if (piston.canPlace()) {
                        // 查找红石火把
                        for (Direction redstoneTorchDirection : DEFAULT_REDSTONE_TORCH_DIRECTIONS) {
                            if (redstoneTorchDirection.getAxis().isVertical()) continue;
                            final BlockPos redstoneTorchPos = piston.offset(redstoneTorchDirection).up();
                            if (redstoneTorchPos.equals(target.pos)) continue;  // 红石火把在目标方块位置
                            if (redstoneTorchPos.equals(piston.offset(piston.facing))) continue;  // 红石火把在活塞头位置
                            for (Direction redstoneTorchFacing : DEFAULT_REDSTONE_TORCH_FACINGS) {
                                if (redstoneTorchDirection == Direction.UP && redstoneTorchFacing == Direction.UP)
                                    continue;    // 活塞上方
                                if (redstoneTorchFacing == Direction.DOWN) continue;  // 红石火把不能倒放
                                // 红石火把底座预检查
                                BlockPos basePos = redstoneTorchPos.offset(redstoneTorchFacing.getOpposite());
                                if (basePos.equals(piston.pos)) continue;  // 底座是活塞
                                final TaskSchemeBaseBlock baseBlock = new TaskSchemeBaseBlock(piston.world, basePos, redstoneTorchDirection, redstoneTorchFacing);
                                final TaskSchemeRedstoneTorchBlock redstoneTorch = new TaskSchemeRedstoneTorchBlock(piston.world, redstoneTorchPos, redstoneTorchDirection, redstoneTorchFacing);
                                if (redstoneTorch.canPlace() && (baseBlock.sideCoversSmallSquare() || baseBlock.canPlace())) {
                                    this.piston = piston;
                                    this.redstoneTorch = redstoneTorch;
                                    this.baseBlock = baseBlock;
                                    this.processingState(TaskState.FIND_PLACE);
                                    return;
                                }
                            }
                        }
                        for (Direction redstoneTorchDirection : DEFAULT_REDSTONE_TORCH_DIRECTIONS) {
                            final BlockPos redstoneTorchPos = piston.offset(redstoneTorchDirection);
                            if (redstoneTorchPos.equals(target.pos)) continue;  // 红石火把在目标方块位置
                            if (redstoneTorchPos.equals(piston.offset(piston.facing))) continue;  // 红石火把在活塞头位置
                            for (Direction redstoneTorchFacing : DEFAULT_REDSTONE_TORCH_FACINGS) {
                                if (redstoneTorchDirection == Direction.UP && redstoneTorchFacing == Direction.UP)
                                    continue;    // 活塞上方
                                if (redstoneTorchFacing == Direction.DOWN) continue;  // 红石火把不能倒放
                                // 红石火把底座预检查
                                BlockPos basePos = redstoneTorchPos.offset(redstoneTorchFacing.getOpposite());
                                if (basePos.equals(piston.pos)) continue;  // 底座是活塞
                                final TaskSchemeRedstoneTorchBlock redstoneTorch = new TaskSchemeRedstoneTorchBlock(piston.world, redstoneTorchPos, redstoneTorchDirection, redstoneTorchFacing);
                                final TaskSchemeBaseBlock baseBlock = new TaskSchemeBaseBlock(piston.world, basePos, redstoneTorchDirection, redstoneTorchFacing);
                                if (redstoneTorch.canPlace() && (baseBlock.sideCoversSmallSquare() || baseBlock.canPlace())) {
                                    this.piston = piston;
                                    this.redstoneTorch = redstoneTorch;
                                    this.baseBlock = baseBlock;
                                    this.processingState(TaskState.FIND_PLACE);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (this.piston == null || this.redstoneTorch == null || this.baseBlock == null) {
            this.debug("没有找到红石火把模式的放置方案");
            this.processingState(TaskState.FAIL);
            return;
        }

        if (this.piston.getBlock() instanceof PistonBlock) {

        } else if (this.piston.canPlace()) {
            if (this.piston.isNeedModifyLook() && !this.piston.isModifyLook(this)) {
                this.piston.setModifyLook(this);
                return;
            }
            BlockPlacerUtils.placement(this.piston.pos, this.piston.facing, Items.PISTON, Items.STICKY_PISTON);
            this.blockRecyclingQueue.add(this.piston.pos);
        }
    }

    private void findPlaceLever() {
    }

    private void findPlace() {
        switch (this.mode) {
            case REDSTONE_TORCH -> this.findPlaceRedstoneTorch();
            case LEVER -> this.findPlaceLever();
            default -> throw new IllegalStateException("Unexpected value: " + this.mode);
        }
    }

    private void execute() {
    }

    private void timeout() {
        this.debug("任务超时");
        this.processingState(TaskState.FAIL);
    }

    private void fail() {
        this.debug("任务失败");
        this.fail = true;
        this.processingState(TaskState.BLOCK_RECYCLING);
    }

    private void blockRecycling() {
        if (!this.blockRecyclingQueue.isEmpty()) {
            BlockPos blockPos = this.blockRecyclingQueue.peek();
            this.debug("回收方块: (%s)", blockPos.toShortString());
            if (ClientPlayerInteractionManagerUtils.updateBlockBreakingProgress(blockPos) && this.target.world.getBlockState(blockPos).isReplaceable()) {
                this.blockRecyclingQueue.remove(blockPos);
            }
        }
        if (this.blockRecyclingQueue.isEmpty()) {
            this.debug("无可回收方块，任务完成");
            this.processingState(TaskState.COMPLETE);
        }
    }

    private void complete() {
        this.debug("任务完成！！！");
    }

    private void processingState(@Nullable TaskState state) {
        if (state != null) {
            this.debug("设置状态: %s", state);
            this.state = state;
        }
        switch (this.state) {
            case INITIALIZE -> this.init(false);
            case WAIT_GAME_UPDATE -> this.waitGameUpdate();
            case WAIT_CUSTOM_UPDATE -> this.waitCustomUpdate();
            case FIND_PLACE -> this.findPlace();
            case EXECUTE -> this.execute();
            case TIMEOUT -> this.timeout();
            case FAIL -> this.fail();
            case BLOCK_RECYCLING -> this.blockRecycling();
            case COMPLETE -> this.complete();
        }
    }

    public void tick() {
        debug("开始");
        if (isComplete()) {
            return;
        }
        if (this.tickCount >= this.tickMax) {
            this.state = TaskState.TIMEOUT;
        }
        this.processingState(null);
        debug("结束\n");
        ++this.tickCount;
    }

    public void debug(String var1, Object... var2) {
        var methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        Debug.alwaysWrite("[%s/%s] [%s/%s] [%s] [%s] [%s] %s",
                this.retryCount, this.retryMax,
                this.tickCount + 1, this.tickMax,
                this.target == null ? "null" : this.target.pos.toShortString(),
                this.state,
                methodName,
                String.format(var1, var2)
        );
    }

    public boolean canInteractWithBlockAt() {
        if (this.target.world == BedrockMiner.world) {
            return true;
        }
        if (this.target.canInteractWithBlockAt(1.0F)) {
            return true;
        }
        if (this.piston != null && this.piston.canInteractWithBlockAt(1.0F)) {
            return true;
        }
        if (this.mode == TaskMode.REDSTONE_TORCH && this.redstoneTorch != null && this.redstoneTorch.canInteractWithBlockAt(1.0F)) {
            return true;
        }
        if (this.mode == TaskMode.LEVER && this.lever != null && this.lever.canInteractWithBlockAt(1.0F)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return Objects.equals(target, task.target);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(target);
    }
}
