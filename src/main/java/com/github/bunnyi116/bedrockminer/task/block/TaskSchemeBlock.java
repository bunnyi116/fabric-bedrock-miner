package com.github.bunnyi116.bedrockminer.task.block;

import com.github.bunnyi116.bedrockminer.task.TaskPlayerLookManager;
import com.github.bunnyi116.bedrockminer.task.Task;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import com.github.bunnyi116.bedrockminer.task.TaskBlock;

public class TaskSchemeBlock extends TaskBlock {
    public final Direction direction;
    public final Direction facing;

    public TaskSchemeBlock(ClientWorld world, BlockPos pos, Direction direction, Direction facing) {
        super(world, pos);
        this.direction = direction;
        this.facing = facing;
    }


    public void setModifyLook(Task task) {
        TaskPlayerLookManager.INSTANCE.setFacing(this.facing, task, this);
    }

    public boolean isModifyLook(Task task) {
        return TaskPlayerLookManager.INSTANCE.isModify(task, this);
    }

    public boolean isNeedModifyLook() {
        return this.facing.getAxis().isHorizontal();
    }
}
