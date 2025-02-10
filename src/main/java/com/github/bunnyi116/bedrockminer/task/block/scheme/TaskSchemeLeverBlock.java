package com.github.bunnyi116.bedrockminer.task.block.scheme;

import com.github.bunnyi116.bedrockminer.util.ClientPlayerInteractionManagerUtils;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import com.github.bunnyi116.bedrockminer.task.block.TaskSchemeBlock;

public class TaskSchemeLeverBlock extends TaskSchemeBlock {
    public TaskSchemeLeverBlock(ClientWorld world, BlockPos pos, Direction direction, Direction facing) {
        super(world, pos, direction, facing);
    }

    @Override
    public boolean isNeedModifyLook() {
        return true;
    }

    public @Nullable Direction getBlockStateFacing() {
        if (this.getBlock() instanceof LeverBlock) {
            return switch (this.get(LeverBlock.FACE)) {
                case FLOOR -> Direction.DOWN;
                case WALL -> this.get(LeverBlock.FACING);
                case CEILING -> Direction.UP;
            };
        }
        return null;
    }

    public boolean canPlace() {
        final BlockState blockState = Blocks.LEVER.getDefaultState();
        blockState.with(LeverBlock.FACE, switch (this.facing) {
            case UP -> BlockFace.CEILING;
            case DOWN -> BlockFace.FLOOR;
            case NORTH, SOUTH, WEST, EAST -> BlockFace.WALL;
        });
        if (this.facing.getAxis().isHorizontal()) {
            blockState.with(WallRedstoneTorchBlock.FACING, this.facing);
        }
        return ClientPlayerInteractionManagerUtils.canPlace(blockState, this.pos);
    }
}
