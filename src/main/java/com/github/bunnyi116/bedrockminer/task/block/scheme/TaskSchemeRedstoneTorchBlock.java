package com.github.bunnyi116.bedrockminer.task.block.scheme;

import com.github.bunnyi116.bedrockminer.util.ClientPlayerInteractionManagerUtils;
import net.minecraft.block.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import com.github.bunnyi116.bedrockminer.task.block.TaskSchemeBlock;

public class TaskSchemeRedstoneTorchBlock extends TaskSchemeBlock {
    public TaskSchemeRedstoneTorchBlock(ClientWorld world, BlockPos pos, Direction direction, Direction facing) {
        super(world, pos, direction, facing);
    }

    @Override
    public boolean isNeedModifyLook() {
        return true;
    }

    public @Nullable Direction getBlockStateFacing() {
        if (this.getBlock() instanceof WallRedstoneTorchBlock) {
            return this.get(WallRedstoneTorchBlock.FACING);
        }
        if (this.getBlock() instanceof RedstoneTorchBlock) {
            return Direction.UP;
        }
        return null;
    }

    public boolean canPlace() {
        if (this.facing == Direction.DOWN) {    // 红石火把不能倒着放置
            return false;
        }
        final boolean isWall = this.facing.getAxis().isHorizontal();
        final Block block = isWall ? Blocks.REDSTONE_WALL_TORCH : Blocks.REDSTONE_TORCH;
        final BlockState blockState = block.getDefaultState();
        if (isWall) {
            blockState.with(WallRedstoneTorchBlock.FACING, this.facing);
        }
        return ClientPlayerInteractionManagerUtils.canPlace(blockState, this.pos);
    }
}
