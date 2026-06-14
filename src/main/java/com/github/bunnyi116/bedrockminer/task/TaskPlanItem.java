package com.github.bunnyi116.bedrockminer.task;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class TaskPlanItem {
    public final int type;
    public final BlockPos pos;
    public final Direction facing;
    public boolean modify;
    public int level;

    public TaskPlanItem(int type, BlockPos pos, Direction facing, int level) {
        this.type = type;
        this.pos = pos;
        this.facing = facing;
        this.modify = false;
        this.level = level;
    }

    public TaskPlanItem(int type, BlockPos pos, Direction facing) {
        this(type, pos, facing, 0);
    }


    public TaskPlanItem(BlockPos pos, Direction facing, int level) {
        this(0, pos, facing, level);
    }

    public TaskPlanItem(BlockPos pos, Direction facing) {
        this(0, pos, facing, 0);
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
