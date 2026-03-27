package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.config.ConfigManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务方案查找器
 */
public class TaskPlanTools {

    public static boolean isApiTask() {
        return !TaskManager.getInstance().isBedrockMinerFeatureEnable();
    }

    public static List<TaskPlan> findAllPossible(BlockPos targetPos) {
        final var schemes = new ArrayList<TaskPlan>();
        for (Direction direction : ConfigManager.getInstance().getConfig().pistonDirections) {
            if (isApiTask() && direction.getAxis().isHorizontal()) {
                continue;
            }
            final var pistons = findPistonPossible(direction, targetPos);
            for (TaskPlanItem piston : pistons) {
                final var redstoneTorches = findRedstoneTorchPossible(direction, targetPos, piston);
                for (TaskPlanItem redstoneTorch : redstoneTorches) {
                    final var slimeBlock = findSlimeBlockPossible(redstoneTorch);
                    schemes.add(new TaskPlan(direction, piston, redstoneTorch, slimeBlock));
                }
            }
        }
//        return schemes.toArray(TaskPlanItem[]::new);
        return schemes;
    }

    private static TaskPlanItem[] findPistonPossible(Direction direction, BlockPos targetPos) {
        final var list = new ArrayList<TaskPlanItem>();
        final var pistonPos = targetPos.relative(direction);
        for (Direction pistonFacing : ConfigManager.getInstance().getConfig().pistonFacings) {
            if (isApiTask() && pistonFacing.getAxis().isHorizontal()) {
                continue;
            }
            // 活塞臂在目标方块位置
            final var pistonHeadPos = pistonPos.relative(pistonFacing);
            if (pistonHeadPos.equals(targetPos))
                continue;
            int level = switch (pistonFacing) {
                case UP -> 0;
                case DOWN -> 1;
                case NORTH, SOUTH, WEST, EAST -> 2;
            };
            list.add(new TaskPlanItem(pistonPos, pistonFacing, level));
        }
        return list.toArray(TaskPlanItem[]::new);
    }

    private static TaskPlanItem[] findRedstoneTorchPossible(Direction direction, BlockPos targetPos, TaskPlanItem pistonInfo) {
        final var list = new ArrayList<TaskPlanItem>();
        final var pistonHeadPos = pistonInfo.pos.relative(pistonInfo.facing);

        // 活塞在目标方块上方，红石火把通过在目标方块下方，充能目标方块激活活塞
        if (direction == Direction.UP) {
            final var redstoneTorchPos = targetPos.relative(direction.getOpposite());
            for (Direction redstoneTorchFacing : ConfigManager.getInstance().getConfig().redstoneTorchFacings) {
                if (isApiTask() && redstoneTorchFacing.getAxis().isHorizontal()) {
                    continue;
                }
                final var basePos = redstoneTorchPos.relative(redstoneTorchFacing.getOpposite());
                if (basePos.equals(pistonInfo.pos) || basePos.equals(pistonHeadPos))
                    continue;

                // 红石火把无法倒置
                if (redstoneTorchFacing == Direction.DOWN)
                    continue;

                // 设置排序等级
                int level = switch (redstoneTorchFacing) {
                    case UP -> 0;
                    case NORTH, SOUTH, WEST, EAST -> 2;
                    default -> throw new IllegalStateException("Unexpected value: " + redstoneTorchFacing);
                };
                // 添加到方案
                list.add(new TaskPlanItem(1, redstoneTorchPos, redstoneTorchFacing, level));
            }
        }

        for (Direction redstoneTorchDirection : ConfigManager.getInstance().getConfig().redstoneTorchDirections) {
            if (isApiTask() && redstoneTorchDirection.getAxis().isHorizontal()) {
                continue;
            }
            final var redstoneTorchPos = pistonInfo.pos.relative(redstoneTorchDirection);
            // 红石火把位置与活塞臂伸出的位置重叠
            if (pistonHeadPos.equals(redstoneTorchPos))
                continue;

            // 常规位置
            for (Direction redstoneTorchFacing : ConfigManager.getInstance().getConfig().redstoneTorchFacings) {
                if (isApiTask() && redstoneTorchFacing.getAxis().isHorizontal()) {
                    continue;
                }
                final var basePos = redstoneTorchPos.relative(redstoneTorchFacing.getOpposite());

                // 过滤红石火把附在活塞上位置
                if (basePos.equals(pistonInfo.pos) || basePos.equals(pistonHeadPos))
                    continue;

                // 红石火把无法倒置
                if (redstoneTorchFacing == Direction.DOWN)
                    continue;

                // 设置排序等级
                int level = switch (redstoneTorchFacing) {
                    case UP -> 0;
                    case NORTH, SOUTH, WEST, EAST -> 2;
                    default -> throw new IllegalStateException("Unexpected value: " + redstoneTorchFacing);
                };

                // 添加到方案
                if (!redstoneTorchPos.equals(targetPos)) {
                    list.add(new TaskPlanItem(redstoneTorchPos, redstoneTorchFacing, level));
                }

                var redstoneTorchPosUp = redstoneTorchPos.above();
                if (!redstoneTorchPosUp.equals(targetPos) && !redstoneTorchPosUp.equals(pistonInfo.pos)) {
                    // 过滤红石火把附在活塞上位置
                    final var baseUpPos = redstoneTorchPos.relative(redstoneTorchFacing.getOpposite());
                    if (baseUpPos.equals(pistonInfo.pos) || baseUpPos.equals(pistonHeadPos))
                        continue;
                    list.add(new TaskPlanItem(redstoneTorchPosUp, redstoneTorchFacing, level + 1));
                }
            }
        }
        return list.toArray(TaskPlanItem[]::new);
    }

    private static TaskPlanItem findSlimeBlockPossible(TaskPlanItem redstoneTorchInfo) {
        final var pos = redstoneTorchInfo.pos;
        final var facing = redstoneTorchInfo.facing;
        return new TaskPlanItem(pos.relative(facing.getOpposite()), facing, facing.getAxis().isVertical() ? 0 : 1);
    }

    // 查找活塞附近的火把
    public static BlockPos[] findPistonNearbyRedstoneTorch(BlockPos pistonPos, ClientLevel world) {
        List<BlockPos> list = new ArrayList<>();
        int range = 3;
        for (Direction direction : Direction.values()) {
            for (int i = 0; i < range; i++) {
                BlockPos pos = pistonPos.relative(direction, i);
                BlockState posState = world.getBlockState(pos);
                if (posState.getBlock() instanceof RedstoneTorchBlock) {
                    list.add(pos);
                }
                BlockPos posUp = pos.above();
                BlockState posUpState = world.getBlockState(posUp);
                if (posUpState.getBlock() instanceof RedstoneTorchBlock) {
                    list.add(posUp);
                }
                BlockPos posDown = pos.below();
                BlockState posDownState = world.getBlockState(posDown);
                if (posDownState.getBlock() instanceof RedstoneTorchBlock) {
                    list.add(posDown);
                }
            }
        }
        return list.toArray(BlockPos[]::new);
    }

}
