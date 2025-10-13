package com.github.bunnyi116.bedrockminer.task;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class TaskSeekBlockInfo {
    public final BlockPos pos;
    public final Direction facing;
    public boolean modify;
    public int level;

    public TaskSeekBlockInfo(BlockPos pos, Direction facing, int level) {
        this.pos = pos;
        this.facing = facing;
        this.modify = false;
        this.level = level;
    }

    public TaskSeekBlockInfo(BlockPos pos, Direction facing) {
        this(pos, facing, 0);
    }

    public boolean isNeedModify() {
        return facing.getAxis().isHorizontal();
    }

    @Override
    public String toString() {
        return "TaskSeekBlockInfo{" +
                "pos=" + pos +
                ", facing=" + facing +
                ", level=" + level +
                '}';
    }
}
