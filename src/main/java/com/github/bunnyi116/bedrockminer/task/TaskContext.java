package com.github.bunnyi116.bedrockminer.task;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.Objects;

@Getter
@Setter
public class TaskContext {
    public static final int MAX_TICKS = 40;

    public final ClientLevel level;
    public final Block block;
    public final BlockPos pos;
    public final boolean simpleMode;
    public final int retryMax;

    private long startTicks;
    private int retryCount;
    private boolean timeout;
    private boolean completed;

    public TaskContext(ClientLevel level, Block block, BlockPos pos, boolean simpleMode, int retryMax) {
        this.level = level;
        this.block = block;
        this.pos = pos;
        this.simpleMode = simpleMode;
        this.retryMax = retryMax;
        this.init();
    }

    public TaskContext(Pending pending, boolean simpleMode, int retryMax) {
        this(pending.level, pending.block, pending.pos, simpleMode, retryMax);
    }

    public void init() {
        this.startTicks = getCurrentTicks();
        this.timeout = false;
        this.completed = false;
    }

    public boolean isTimeout() {
        return this.timeout || (getCurrentTicks() - this.startTicks) > MAX_TICKS;
    }

    private long getCurrentTicks() {
        return TaskManager.getInstance().getTicks();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TaskContext that = (TaskContext) o;
        return Objects.equals(level, that.level) && Objects.equals(pos, that.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, pos);
    }

    public static class Pending {
        public final ClientLevel level;
        public final Block block;
        public final BlockPos pos;

        public Pending(ClientLevel level, Block block, BlockPos pos) {
            this.level = level;
            this.block = block;
            this.pos = pos;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Pending pending = (Pending) o;
            return Objects.equals(level, pending.level) && Objects.equals(pos, pending.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, pos);
        }
    }
}
