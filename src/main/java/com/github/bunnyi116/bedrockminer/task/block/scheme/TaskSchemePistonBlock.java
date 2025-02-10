package com.github.bunnyi116.bedrockminer.task.block.scheme;

import com.github.bunnyi116.bedrockminer.util.ClientPlayerInteractionManagerUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import com.github.bunnyi116.bedrockminer.task.block.TaskSchemeBlock;

public class TaskSchemePistonBlock extends TaskSchemeBlock {
    public TaskSchemePistonBlock(ClientWorld world, BlockPos pos, Direction direction, Direction facing) {
        super(world, pos, direction, facing);
    }

    public @Nullable Direction getBlockStateFacing() {
        if (this.getBlock() instanceof PistonBlock) {
            return this.get(PistonBlock.FACING);
        }
        return null;
    }

    public boolean canPlace() {
        final BlockState pistonState = Blocks.PISTON.getDefaultState();
        pistonState.with(PistonBlock.FACING, this.facing);
        final BlockState pistonHeadState = Blocks.PISTON_HEAD.getDefaultState();
        pistonState.with(PistonBlock.FACING, this.facing);
        return ClientPlayerInteractionManagerUtils.canPlace(pistonState, this.pos) && ClientPlayerInteractionManagerUtils.canPlace(pistonHeadState, this.pos.offset(this.facing));
    }
}
