package yan.lx.bedrockminer.task;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
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
        List<TaskSeekSchemeInfo> schemes = new ArrayList<>();
        for (Direction direction : directions) {
            TaskSeekBlockInfo[] pistons = findPistonPossible(direction, targetPos);
            for (TaskSeekBlockInfo piston : pistons) {
                TaskSeekBlockInfo[] redstoneTorches = findRedstoneTorchPossible(piston);
                for (TaskSeekBlockInfo redstoneTorch : redstoneTorches) {
                    TaskSeekBlockInfo slimeBlock = findSlimeBlockPossible(redstoneTorch);
                    schemes.add(new TaskSeekSchemeInfo(direction, piston, redstoneTorch, slimeBlock));
                }
            }
        }
        // 重新排序
        schemes.sort((o1, o2) -> {
            int cr = 0;
            int a = o1.piston.level - o2.piston.level;
            if (a != 0) {
                cr = (a > 0) ? 3 : -1;
            } else {
                a = o1.redstoneTorch.level - o2.redstoneTorch.level;
                if (a != 0) {
                    cr = (a > 0) ? 2 : -2;
                } else {
                    a = o1.slimeBlock.level - o2.slimeBlock.level;
                    if (a != 0) {
                        cr = (a > 0) ? 1 : -3;
                    }
                }
            }
            return cr;
        });
        return schemes.toArray(TaskSeekSchemeInfo[]::new);
    }

    private static TaskSeekBlockInfo[] findPistonPossible(Direction direction, BlockPos targetPos) {
        List<TaskSeekBlockInfo> list = new ArrayList<>();
        BlockPos pos = targetPos.offset(direction);
        for (Direction facing : Direction.values()) {
            // 过滤朝着目标方块的方向
            if (direction == facing.getOpposite()) continue;
            int level = switch (facing) {
                case UP -> 0;
                case DOWN -> 1;
                case NORTH -> 2;
                case SOUTH -> 3;
                case WEST -> 4;
                case EAST -> 5;
            };
            list.add(new TaskSeekBlockInfo(pos, facing, level));
        }
        return list.toArray(TaskSeekBlockInfo[]::new);
    }

    private static TaskSeekBlockInfo[] findRedstoneTorchPossible(TaskSeekBlockInfo pistonInfo) {
        List<TaskSeekBlockInfo> list = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            // 过滤与活塞臂退出的位置
            if (direction == pistonInfo.facing) continue;
            // 红石火把的方向集合, 因为红石火把没有倒着放, 所以去掉了他
            List<Direction> facings = List.of(Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
            BlockPos pos = pistonInfo.pos.offset(direction);
            // 活塞底部位置, 并且过滤活塞伸出方向位置
            for (Direction facing : facings) {
                if (direction == facing) continue;
                // 方案在上活塞底下是目标方块, 所以过滤
                if (direction == Direction.UP) continue;
                // 活塞朝下, 那么活塞下面就被活塞臂占位, 所以过滤
                if (pistonInfo.facing == Direction.DOWN) continue;
                int level = switch (facing) {
                    case UP -> 0;
                    case NORTH -> 1;
                    case SOUTH -> 2;
                    case WEST -> 3;
                    case EAST -> 4;
                    default -> throw new IllegalStateException("Unexpected value: " + facing);
                };
                list.add(new TaskSeekBlockInfo(pos, facing, level));
            }
            // 常规位置
            for (Direction facing : facings) {
                // 过滤活塞测方向
                if (direction == facing) continue;
                // 过滤垂直方向
                if (direction.getAxis().isVertical()) continue;
                // 过滤红石火把附在活塞面上位置
                if (facing == pistonInfo.facing) continue;
                int level = switch (facing) {
                    case UP -> 0;
                    case NORTH -> 1;
                    case SOUTH -> 2;
                    case WEST -> 3;
                    case EAST -> 4;
                    default -> throw new IllegalStateException("Unexpected value: " + facing);
                };
                list.add(new TaskSeekBlockInfo(pos, facing, level));
                list.add(new TaskSeekBlockInfo(pos.up(), facing, level));

            }


        }
        return list.toArray(TaskSeekBlockInfo[]::new);
    }

    private static TaskSeekBlockInfo findSlimeBlockPossible(TaskSeekBlockInfo redstoneTorchInfo) {
        BlockPos pos = redstoneTorchInfo.pos;
        Direction facing = redstoneTorchInfo.facing;
        return new TaskSeekBlockInfo(pos.offset(facing.getOpposite()), facing, facing == Direction.UP ? 0 : 1);
    }

    /**
     * 查找活塞附近的火把
     */
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
