package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.config.ConfigManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务方案查找器
 */
public class TaskPlanTools {

    public static List<TaskPlan> findAllPossible(BlockPos targetPos, ClientWorld world) {
        final ArrayList<TaskPlan> schemes = new ArrayList<>();
        for (Direction direction : ConfigManager.getInstance().getConfig().pistonDirections) {
            final TaskPlanItem[] pistons = findPistonPossible(direction, targetPos);
            for (TaskPlanItem piston : pistons) {
                final TaskPlanItem[] redstoneTorches = findRedstoneTorchPossible(direction, targetPos, piston);
                for (TaskPlanItem redstoneTorch : redstoneTorches) {
                    final TaskPlanItem slimeBlock = findSlimeBlockPossible(redstoneTorch);
                    schemes.add(new TaskPlan(direction, piston, redstoneTorch, slimeBlock));
                }
            }
        }
//        return schemes.toArray(TaskPlanItem[]::new);
        return schemes;
    }

    private static TaskPlanItem[] findPistonPossible(Direction direction, BlockPos targetPos) {
        final ArrayList<TaskPlanItem> list = new ArrayList<>();
        final BlockPos pistonPos = targetPos.offset(direction);
        for (Direction pistonFacing : ConfigManager.getInstance().getConfig().pistonFacings) {
            // 活塞臂在目标方块位置
            final BlockPos pistonHeadPos = pistonPos.offset(pistonFacing);
            if (pistonHeadPos.equals(targetPos))
                continue;
            int level;
            switch (pistonFacing) {
                case UP:
                    level = 0;
                    break;
                case DOWN:
                    level = 1;
                    break;
                case NORTH:
                case SOUTH:
                case WEST:
                case EAST:
                    level = 2;
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            list.add(new TaskPlanItem(pistonPos, pistonFacing, level));
        }
        return list.toArray(TaskPlanItem[]::new);
    }

    private static TaskPlanItem[] findRedstoneTorchPossible(Direction direction, BlockPos targetPos, TaskPlanItem pistonInfo) {
        final ArrayList<TaskPlanItem> list = new ArrayList<>();
        final BlockPos pistonHeadPos = pistonInfo.pos.offset(pistonInfo.facing);

        // 活塞在目标方块上方，红石火把通过在目标方块下方，充能目标方块激活活塞
        if (direction == Direction.UP) {
            final BlockPos redstoneTorchPos = targetPos.offset(direction.getOpposite());
            for (Direction redstoneTorchFacing : ConfigManager.getInstance().getConfig().redstoneTorchFacings) {
                final BlockPos basePos = redstoneTorchPos.offset(redstoneTorchFacing.getOpposite());
                if (basePos.equals(pistonInfo.pos) || basePos.equals(pistonHeadPos))
                    continue;

                // 红石火把无法倒置
                if (redstoneTorchFacing == Direction.DOWN)
                    continue;

                // 设置排序等级
                int level;
                switch (redstoneTorchFacing) {
                    case UP:
                        level = 0;
                        break;
                    case NORTH:
                    case SOUTH:
                    case WEST:
                    case EAST:
                        level = 2;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + redstoneTorchFacing);
                }
                // 添加到方案
                list.add(new TaskPlanItem(1, redstoneTorchPos, redstoneTorchFacing, level));
            }
        }

        for (Direction redstoneTorchDirection : ConfigManager.getInstance().getConfig().redstoneTorchDirections) {
            final BlockPos redstoneTorchPos = pistonInfo.pos.offset(redstoneTorchDirection);
            // 红石火把位置与活塞臂伸出的位置重叠
            if (pistonHeadPos.equals(redstoneTorchPos))
                continue;

            // 常规位置
            for (Direction redstoneTorchFacing : ConfigManager.getInstance().getConfig().redstoneTorchFacings) {
                final BlockPos basePos = redstoneTorchPos.offset(redstoneTorchFacing.getOpposite());

                // 过滤红石火把附在活塞上位置
                if (basePos.equals(pistonInfo.pos) || basePos.equals(pistonHeadPos))
                    continue;

                // 红石火把无法倒置
                if (redstoneTorchFacing == Direction.DOWN)
                    continue;

                // 设置排序等级
                int level;
                switch (redstoneTorchFacing) {
                    case UP:
                        level = 0;
                        break;
                    case NORTH:
                    case SOUTH:
                    case WEST:
                    case EAST:
                        level = 2;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + redstoneTorchFacing);
                }

                // 添加到方案
                if (!redstoneTorchPos.equals(targetPos)) {
                    list.add(new TaskPlanItem(redstoneTorchPos, redstoneTorchFacing, level));
                }

                BlockPos redstoneTorchPosUp = redstoneTorchPos.up();
                if (!redstoneTorchPosUp.equals(targetPos) && !redstoneTorchPosUp.equals(pistonInfo.pos)) {
                    // 过滤红石火把附在活塞上位置
                    final BlockPos baseUpPos = redstoneTorchPos.offset(redstoneTorchFacing.getOpposite());
                    if (baseUpPos.equals(pistonInfo.pos) || baseUpPos.equals(pistonHeadPos))
                        continue;
                    list.add(new TaskPlanItem(redstoneTorchPos.up(), redstoneTorchFacing, level + 1));
                }
            }
        }
        return list.toArray(TaskPlanItem[]::new);
    }

    private static TaskPlanItem findSlimeBlockPossible(TaskPlanItem redstoneTorchInfo) {
        final BlockPos pos = redstoneTorchInfo.pos;
        final Direction facing = redstoneTorchInfo.facing;
        return new TaskPlanItem(pos.offset(facing.getOpposite()), facing, facing.getAxis().isVertical() ? 0 : 1);
    }

    // 查找活塞附近的火把
    public static BlockPos[] findPistonNearbyRedstoneTorch(BlockPos pistonPos, ClientWorld world) {
        List<BlockPos> list = new ArrayList<>();
        int range = 3;
        for (Direction direction : Direction.values()) {
            for (int i = 0; i < range; i++) {
                BlockPos pos = pistonPos.offset(direction, i);
                BlockState posState = world.getBlockState(pos);
                if (posState.getBlock() instanceof RedstoneTorchBlock) {
                    list.add(pos);
                }
                BlockPos posUp = pos.up();
                BlockState posUpState = world.getBlockState(posUp);
                if (posUpState.getBlock() instanceof RedstoneTorchBlock) {
                    list.add(posUp);
                }
                BlockPos posDown = pos.down();
                BlockState posDownState = world.getBlockState(posDown);
                if (posDownState.getBlock() instanceof RedstoneTorchBlock) {
                    list.add(posDown);
                }
            }
        }
        return list.toArray(BlockPos[]::new);
    }

}
