package com.github.bunnyi116.bedrockminer.task.block.scheme;

import com.github.bunnyi116.bedrockminer.util.ClientPlayerInteractionManagerUtils;
import net.minecraft.block.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import com.github.bunnyi116.bedrockminer.task.block.TaskSchemeBlock;

public class TaskSchemeBaseBlock extends TaskSchemeBlock {
    public TaskSchemeBaseBlock(ClientWorld world, BlockPos pos, Direction direction, Direction facing) {
        super(world, pos, direction, facing);
    }

    public boolean sideCoversSmallSquare() {
        return Block.sideCoversSmallSquare(this.world, this.pos, this.facing);
    }

    @Override
    public boolean isNeedModifyLook() {
        return false;
    }


    public boolean canPlace() {
        return ClientPlayerInteractionManagerUtils.canPlace(Blocks.SLIME_BLOCK.getDefaultState(), this.pos);
    }
}
