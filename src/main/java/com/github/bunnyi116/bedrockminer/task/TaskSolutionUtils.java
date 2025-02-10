package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.task.block.scheme.TaskSchemeLeverBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import com.github.bunnyi116.bedrockminer.task.block.TaskTargetBlock;
import com.github.bunnyi116.bedrockminer.task.block.scheme.TaskSchemeBaseBlock;
import com.github.bunnyi116.bedrockminer.task.block.scheme.TaskSchemePistonBlock;
import com.github.bunnyi116.bedrockminer.task.block.scheme.TaskSchemeRedstoneTorchBlock;

import java.util.ArrayList;
import java.util.List;

public class TaskSolutionUtils {
    public final static Direction[] DEFAULT_PISTON_DIRECTIONS = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    public final static Direction[] DEFAULT_PISTON_FACINGS = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public final static Direction[] DEFAULT_REDSTONE_TORCH_DIRECTIONS = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    public final static Direction[] DEFAULT_REDSTONE_TORCH_FACINGS = new Direction[]{Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public final static Direction[] DEFAULT_LEVER_DIRECTIONS = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    public final static Direction[] DEFAULT_LEVER_FACINGS = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};


    public static TaskSchemePistonBlock[] findPistons(TaskTargetBlock targetBlock) {
        List<TaskSchemePistonBlock> pistons = new ArrayList<>();
        for (Direction pistonDirection : DEFAULT_PISTON_DIRECTIONS) {
            final BlockPos pistonPos = targetBlock.offset(pistonDirection);
            for (Direction pistonFacing : DEFAULT_PISTON_FACINGS) {
                final BlockPos pistonHeadPos = pistonPos.offset(pistonFacing);
                if (pistonHeadPos.equals(targetBlock.pos)) continue;    // 活塞头在目标方块位置
                pistons.add(new TaskSchemePistonBlock(targetBlock.world, pistonPos, pistonDirection, pistonFacing));
            }
        }
        return pistons.toArray(new TaskSchemePistonBlock[0]);
    }

    public static TaskSchemeRedstoneTorchBlock[] findRedstoneTorchs(TaskTargetBlock target, TaskSchemePistonBlock piston) {
        List<TaskSchemeRedstoneTorchBlock> redstoneTorchs = new ArrayList<>();
        for (Direction redstoneTorchDirection : DEFAULT_REDSTONE_TORCH_DIRECTIONS) {
            if (redstoneTorchDirection.getAxis().isVertical()) continue;
            final BlockPos redstoneTorchPos = piston.offset(redstoneTorchDirection).up();
            if (redstoneTorchPos.equals(target.pos)) continue;  // 红石火把在目标方块位置
            if (redstoneTorchPos.equals(piston.offset(piston.facing))) continue;  // 红石火把在活塞头位置
            for (Direction redstoneTorchFacing : DEFAULT_REDSTONE_TORCH_FACINGS) {
                if (redstoneTorchDirection == Direction.UP && redstoneTorchFacing == Direction.UP) continue;    // 活塞上方
                if (redstoneTorchFacing == Direction.DOWN) continue;  // 红石火把不能倒放
                // 红石火把底座预检查
                BlockPos basePos = redstoneTorchPos.offset(redstoneTorchFacing.getOpposite());
                if (basePos.equals(piston.pos)) continue;  // 底座是活塞
                redstoneTorchs.add(new TaskSchemeRedstoneTorchBlock(piston.world, redstoneTorchPos, redstoneTorchDirection, redstoneTorchFacing));
            }
        }
        for (Direction redstoneTorchDirection : DEFAULT_REDSTONE_TORCH_DIRECTIONS) {
            final BlockPos redstoneTorchPos = piston.offset(redstoneTorchDirection);
            if (redstoneTorchPos.equals(target.pos)) continue;  // 红石火把在目标方块位置
            if (redstoneTorchPos.equals(piston.offset(piston.facing))) continue;  // 红石火把在活塞头位置
            for (Direction redstoneTorchFacing : DEFAULT_REDSTONE_TORCH_FACINGS) {
                if (redstoneTorchDirection == Direction.UP && redstoneTorchFacing == Direction.UP) continue;    // 活塞上方
                if (redstoneTorchFacing == Direction.DOWN) continue;  // 红石火把不能倒放
                // 红石火把底座预检查
                BlockPos basePos = redstoneTorchPos.offset(redstoneTorchFacing.getOpposite());
                if (basePos.equals(piston.pos)) continue;  // 底座是活塞
                redstoneTorchs.add(new TaskSchemeRedstoneTorchBlock(piston.world, redstoneTorchPos, redstoneTorchDirection, redstoneTorchFacing));
            }
        }
        return redstoneTorchs.toArray(new TaskSchemeRedstoneTorchBlock[0]);
    }

    public static TaskSchemeLeverBlock[] findLevers(TaskTargetBlock target, TaskSchemePistonBlock piston) {
        List<TaskSchemeLeverBlock> levers = new ArrayList<>();
        for (Direction leverDirection : DEFAULT_LEVER_DIRECTIONS) {
            final BlockPos leverPos = target.offset(leverDirection);
            if (leverPos.equals(piston.pos)) continue;  // 在活塞位置
            if (leverPos.equals(piston.offset(piston.facing))) continue;  // 在活塞头位置
            for (Direction leverFacing : DEFAULT_LEVER_FACINGS) {
                BlockPos basePos = leverPos.offset(leverFacing.getOpposite());
                if (basePos.equals(piston.pos)) continue;  // 底座是活塞
                levers.add(new TaskSchemeLeverBlock(piston.world, leverPos, leverDirection, leverFacing));
            }
        }
        for (Direction leverDirection : DEFAULT_LEVER_DIRECTIONS) {
            final BlockPos leverPos = piston.offset(leverDirection);
            if (leverPos.equals(target.pos)) continue;  // 在目标方块位置
            if (leverPos.equals(piston.offset(piston.facing))) continue;  // 在活塞头位置
            for (Direction leverFacing : DEFAULT_LEVER_FACINGS) {
                BlockPos basePos = leverPos.offset(leverFacing.getOpposite());
                if (basePos.equals(piston.pos)) continue;  // 底座是活塞
                levers.add(new TaskSchemeLeverBlock(piston.world, leverPos, leverDirection, leverFacing));
            }
        }
        return levers.toArray(new TaskSchemeLeverBlock[0]);
    }

    public static TaskSchemeBaseBlock findRedstoneTorchBaseBlock(TaskSchemeRedstoneTorchBlock redstoneTorch) {
        BlockPos basePos = redstoneTorch.offset(redstoneTorch.facing.getOpposite());
        return new TaskSchemeBaseBlock(redstoneTorch.world, basePos, redstoneTorch.direction, redstoneTorch.facing);
    }

    public static TaskSchemeBaseBlock findLeverBaseBlock(TaskSchemeLeverBlock leverBlock) {
        BlockPos basePos = leverBlock.offset(leverBlock.facing.getOpposite());
        return new TaskSchemeBaseBlock(leverBlock.world, basePos, leverBlock.direction, leverBlock.facing);
    }
}
