package com.github.bunnyi116.bedrockminer.task;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 破基岩方案搜索器。
 * <p>
 * 参考 litematica-printer 的定向搜索策略，按优先级逐方向查找：
 * 1. 垂直方向（UP > DOWN）—— 最常用，时序稳定
 * 2. 水平方向（N/S/E/W）—— 备选方案
 * <p>
 * 红石火把位置优先级：顶部清洁 > 墙上 > 下一格顶部
 */
public class TaskPlanTools {

    private static final Direction[] VERTICAL_DIRECTIONS = {Direction.UP, Direction.DOWN};
    private static final Direction[] HORIZONTAL_DIRECTIONS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final Direction[] TORCH_ATTACH_DIRECTIONS = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    /**
     * 为目标方块查找最优破坏方案列表（优先级排序）。
     * 替代原有的全量枚举（~720 组合），按定向搜索生成约 30-60 个高质量候选。
     *
     * @param targetPos 目标基岩位置
     * @return 优先级排序的方案列表
     */
    public static List<Scheme> findBestSchemes(BlockPos targetPos) {
        List<Scheme> schemes = new ArrayList<>();

        // 阶段1：垂直方向（最优先）
        for (Direction dir : VERTICAL_DIRECTIONS) {
            searchVerticalSchemes(schemes, targetPos, dir);
        }

        // 阶段2：水平方向（备用）
        for (Direction dir : HORIZONTAL_DIRECTIONS) {
            searchHorizontalSchemes(schemes, targetPos, dir);
        }

        // 按总成本升序（低成本优先）
        schemes.sort(Comparator.comparingInt(TaskPlanTools::schemeCost));

        return schemes;
    }

    /**
     * 为保持向后兼容，保留旧接口，内部调用新方法。
     */
    public static List<Scheme> findAllPossible(BlockPos targetPos) {
        return findBestSchemes(targetPos);
    }

    // ---- 垂直方向搜索 ------------------------------------------------

    private static void searchVerticalSchemes(List<Scheme> out, BlockPos targetPos, Direction direction) {
        BlockPos pistonPos = targetPos.relative(direction);
        if (!Level.isInSpawnableBounds(pistonPos)) return;

        // 活塞朝向与偏移方向相同（垂直），活塞臂不可指向自身
        Direction pistonFacing = direction;
        if (pistonPos.relative(pistonFacing).equals(targetPos)) return;

        int pistonLevel = direction == Direction.UP ? 0 : 1;
        SchemeBlock piston = new SchemeBlock(pistonPos, pistonFacing, pistonLevel);

        // 搜索红石火把位置：顶部优先 → 墙上 → 下一格
        searchTorchPlacements(out, direction, piston, targetPos);
    }

    // ---- 水平方向搜索 ------------------------------------------------

    private static void searchHorizontalSchemes(List<Scheme> out, BlockPos targetPos, Direction direction) {
        BlockPos pistonPos = targetPos.relative(direction);
        if (!Level.isInSpawnableBounds(pistonPos)) return;

        // 水平方向时，活塞可朝上或朝下
        for (Direction facing : new Direction[]{Direction.UP, Direction.DOWN}) {
            BlockPos headPos = pistonPos.relative(facing);
            if (headPos.equals(targetPos)) continue;

            int pistonLevel = facing == Direction.UP ? 0 : 1;
            SchemeBlock piston = new SchemeBlock(pistonPos, facing, pistonLevel);

            searchTorchPlacements(out, direction, piston, targetPos);
        }
    }

    // ---- 红石火把位置搜索 --------------------------------------------

    /**
     * 按优先级搜索红石火把位置：
     * 1. 顶部红石火把（底座在活塞相邻侧面上方）—— 清洁优先
     * 2. 墙上红石火把（火把位置在活塞相邻侧面，底座在邻块）
     * 3. 较低顶部红石火把（底座在活塞相邻侧面下方一格）
     * 4. UP 方向特殊：火把放在目标方块下方
     */
    private static void searchTorchPlacements(List<Scheme> out, Direction direction,
                                               SchemeBlock piston, BlockPos targetPos) {
        BlockPos pistonPos = piston.pos;
        BlockPos headPos = pistonPos.relative(piston.facing);
        Direction excludeDir = piston.facing.getOpposite();

        // UP 方向特殊处理：红石火把放在目标方块下方充能
        if (direction == Direction.UP) {
            BlockPos belowTarget = targetPos.below();
            for (Direction facing : TORCH_ATTACH_DIRECTIONS) {
                if (facing == Direction.DOWN) continue;
                BlockPos basePos = belowTarget.relative(facing.getOpposite());
                if (basePos.equals(pistonPos) || basePos.equals(headPos)) continue;
                int level = facing == Direction.UP ? 0 : 2;
                out.add(buildScheme(direction, piston,
                        new SchemeBlock(1, belowTarget, facing, level)));
            }
        }

        // 第一遍：顶部红石火把（清洁优先）
        for (Direction side : HORIZONTAL_DIRECTIONS) {
            if (side == excludeDir) continue;
            BlockPos supportPos = pistonPos.relative(side);
            if (supportPos.equals(targetPos) || supportPos.equals(headPos)) continue;

            BlockPos torchPos = supportPos.above();
            if (torchPos.equals(targetPos) || torchPos.equals(pistonPos)) continue;

            out.add(buildScheme(direction, piston,
                    new SchemeBlock(torchPos, Direction.UP, 0)));
        }

        // 第二遍：墙上红石火把
        for (Direction side : HORIZONTAL_DIRECTIONS) {
            BlockPos torchPos = pistonPos.relative(side);
            if (torchPos.equals(targetPos) || torchPos.equals(headPos)) continue;

            // 尝试附着到周围方块
            for (Direction facing : HORIZONTAL_DIRECTIONS) {
                if (facing.getOpposite() == side) continue; // 不能附着在空气面
                BlockPos supportPos = torchPos.relative(facing.getOpposite());
                if (supportPos.equals(pistonPos) || supportPos.equals(headPos)) continue;
                if (supportPos.equals(targetPos)) continue;

                out.add(buildScheme(direction, piston,
                        new SchemeBlock(1, torchPos, facing, 2)));
            }
        }

        // 第三遍：较低顶部红石火把
        for (Direction side : HORIZONTAL_DIRECTIONS) {
            if (side == excludeDir) continue;
            BlockPos supportPos = pistonPos.relative(side).below();
            if (!Level.isInSpawnableBounds(supportPos)) continue;
            if (supportPos.equals(targetPos) || supportPos.equals(headPos)) continue;

            BlockPos torchPos = supportPos.above();
            if (torchPos.equals(targetPos) || torchPos.equals(pistonPos)) continue;

            out.add(buildScheme(direction, piston,
                    new SchemeBlock(torchPos, Direction.UP, 1)));
        }
    }

    // ---- 辅助方法 ----------------------------------------------------

    private static Scheme buildScheme(Direction direction, SchemeBlock piston, SchemeBlock torch) {
        BlockPos slimePos = torch.pos.relative(torch.facing.getOpposite());
        int slimeLevel = torch.facing.getAxis().isVertical() ? 0 : 1;
        SchemeBlock slime = new SchemeBlock(slimePos, torch.facing, slimeLevel);
        return new Scheme(direction, piston, torch, slime);
    }

    private static int schemeCost(Scheme scheme) {
        return scheme.level + scheme.piston.level + scheme.redstoneTorch.level + scheme.slimeBlock.level;
    }

    // ---- 周边红石火把查找（保留原有实现）-------------------------------

    /**
     * 查找活塞附近的红石火把（用于执行前清除）
     */
    public static BlockPos[] findPistonNearbyRedstoneTorch(BlockPos pistonPos, ClientLevel world) {
        List<BlockPos> list = new ArrayList<>();
        int range = 3;
        for (Direction direction : Direction.values()) {
            for (int i = 0; i < range; i++) {
                BlockPos pos = pistonPos.relative(direction, i);
                if (isRedstoneTorch(world, pos)) list.add(pos);
                if (isRedstoneTorch(world, pos.above())) list.add(pos.above());
                if (isRedstoneTorch(world, pos.below())) list.add(pos.below());
            }
        }
        return list.toArray(BlockPos[]::new);
    }

    private static boolean isRedstoneTorch(ClientLevel world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof RedstoneTorchBlock;
    }
}