package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.Debug;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 任务方案查找器
 */
public class TaskSeekSchemeTools {
    public static Direction[] directions = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /**
     * 查找所有可能放置情况(假设性的，未检查游戏中的环境)
     */
    public static TaskSeekSchemeInfo[] findAllPossible(BlockPos targetPos, ClientWorld world) {
        final var schemes = new ArrayList<TaskSeekSchemeInfo>();
        for (Direction direction : directions) {
            final var pistons = findPistonPossible(direction, targetPos);
            for (TaskSeekBlockInfo piston : pistons) {
                final var redstoneTorches = findRedstoneTorchPossible(direction, targetPos, piston);
                for (TaskSeekBlockInfo redstoneTorch : redstoneTorches) {
                    final var slimeBlock = findSlimeBlockPossible(redstoneTorch);
                    schemes.add(new TaskSeekSchemeInfo(direction, piston, redstoneTorch, slimeBlock));
                }
            }
        }
        // 重新排序
        schemes.sort(Comparator
                .comparingInt((TaskSeekSchemeInfo scheme) -> scheme.piston.level + scheme.redstoneTorch.level)
        );
        return schemes.toArray(TaskSeekSchemeInfo[]::new);
    }

    private static TaskSeekBlockInfo[] findPistonPossible(Direction direction, BlockPos targetPos) {
        final var list = new ArrayList<TaskSeekBlockInfo>();
        final var pistonPos = targetPos.offset(direction);
        for (Direction pistonFacing : Direction.values()) {
            // 活塞臂在目标方块位置
            final var pistonHeadPos = pistonPos.offset(pistonFacing);
            if (pistonHeadPos.equals(targetPos))
                continue;
            int level = switch (pistonFacing) {
                case UP -> 0;
                case DOWN -> 1;
                case NORTH, SOUTH, WEST, EAST -> 2;
            };
            list.add(new TaskSeekBlockInfo(pistonPos, pistonFacing, level));
        }
        return list.toArray(TaskSeekBlockInfo[]::new);
    }

    final static List<Direction> redstoneTorchFacings = List.of(Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    private static TaskSeekBlockInfo[] findRedstoneTorchPossible(Direction direction, BlockPos targetPos, TaskSeekBlockInfo pistonInfo) {
        final var list = new ArrayList<TaskSeekBlockInfo>();
        final var pistonHeadPos = pistonInfo.pos.offset(pistonInfo.facing);

        // 活塞在目标方块上方，红石火把通过在目标方块下方，充能目标方块激活活塞
        if (direction == Direction.UP) {
            final var redstoneTorchPos = targetPos.offset(direction.getOpposite());
            for (Direction facing : redstoneTorchFacings) {
                // 红石火把无法倒置
                if (facing == Direction.DOWN)
                    continue;

                // 设置排序等级
                int level = switch (facing) {
                    case UP -> 0;
                    case NORTH, SOUTH, WEST, EAST -> 2;
                    default -> throw new IllegalStateException("Unexpected value: " + facing);
                };
                // 添加到方案
                list.add(new TaskSeekBlockInfo(redstoneTorchPos, facing, level));
            }
        }

        for (Direction redstoneTorchDirection : Direction.values()) {
            final var redstoneTorchPos = pistonInfo.pos.offset(redstoneTorchDirection);
            // 红石火把位置与活塞臂伸出的位置重叠
            if (pistonHeadPos.equals(redstoneTorchPos))
                continue;

            // 常规位置
            for (Direction redstoneTorchFacing : redstoneTorchFacings) {
                final var basePos = redstoneTorchPos.offset(redstoneTorchFacing.getOpposite());

                // 过滤红石火把附在活塞面上位置
                if (basePos.equals(pistonInfo.pos))
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
                    list.add(new TaskSeekBlockInfo(redstoneTorchPos, redstoneTorchFacing, level));
                }
                var redstoneTorchPosUp = redstoneTorchPos.up();
                if (!redstoneTorchPosUp.equals(targetPos) && !redstoneTorchPosUp.equals(pistonInfo.pos)) {
                    list.add(new TaskSeekBlockInfo(redstoneTorchPos.up(), redstoneTorchFacing, level + 1));
                }
            }
        }
        return list.toArray(TaskSeekBlockInfo[]::new);
    }

    private static TaskSeekBlockInfo findSlimeBlockPossible(TaskSeekBlockInfo redstoneTorchInfo) {
        BlockPos pos = redstoneTorchInfo.pos;
        Direction facing = redstoneTorchInfo.facing;
        return new TaskSeekBlockInfo(pos.offset(facing.getOpposite()), facing, facing.getAxis().isVertical() ? 0 : 1);
    }

    // 查找活塞附近的火把
    public static BlockPos[] findPistonNearbyRedstoneTorch(BlockPos pistonPos, ClientWorld world) {
        List<BlockPos> list = new ArrayList<>();
        // 查找活塞2格范围内的红石火把, 之所以是2格, 是为了避免强充能方块边上被激活
        int range = 2;
        for (Direction direction : Direction.values()) {
            for (int i = 0; i < range; i++) {
                BlockPos pos = pistonPos.offset(direction, i);
                BlockState blockState = world.getBlockState(pos);
                if (blockState.isOf(Blocks.REDSTONE_TORCH) || blockState.isOf(Blocks.REDSTONE_WALL_TORCH)) {
                    list.add(pos);
                }
            }
        }
        return list.toArray(BlockPos[]::new);
    }

}
