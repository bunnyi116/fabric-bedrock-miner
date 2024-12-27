package yan.lx.bedrockminer.utils;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static yan.lx.bedrockminer.BedrockMiner.player;

public class InteractionUtils {
    public static Direction getClosestFace(BlockPos targetPos) {
        Vec3d playerPos = player.getEyePos();
        Vec3d targetCenterPos = targetPos.toCenterPos();
        Direction closestFace = null;
        double closestDistanceSquared = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            double offsetX = direction.getOffsetX() * 0.5;
            double offsetY = direction.getOffsetY() * 0.5;
            double offsetZ = direction.getOffsetZ() * 0.5;
            Vec3d facePos = targetCenterPos.add(offsetX, offsetY, offsetZ);
            double distanceSquared = playerPos.squaredDistanceTo(facePos);
            // 更新最近的面
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closestFace = direction;
            }
        }
        return closestFace;
    }

    public static boolean isBlockWithinReach(BlockPos targetPos) {
        return isBlockWithinReach(targetPos, getClosestFace(targetPos));
    }

    public static boolean isBlockWithinReach(BlockPos targetPos, Direction side) {
        return isBlockWithinReach(targetPos, getClosestFace(targetPos), 0);
    }

    public static boolean isBlockWithinReach(BlockPos targetPos, Direction side, double deltaReachDistance) {
        double reachDistance = getPlayerBlockInteractionRange() + deltaReachDistance;
        Vec3d playerPos = player.getEyePos();
        Vec3d targetCenterPos = targetPos.toCenterPos();
        // 定义面上的关键点（四个角 + 中心点）
        List<Vec3d> facePoints = getFacePoints(targetCenterPos, side);
        // 遍历该面所有关键点，找到最短距离
        for (Vec3d point : facePoints) {
            double distanceSquared = playerPos.squaredDistanceTo(point);
            if (distanceSquared <= reachDistance * reachDistance) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取目标面上的多个关键点
     */
    private static List<Vec3d> getFacePoints(Vec3d center, Direction side) {
        List<Vec3d> points = new ArrayList<>();
        double halfSize = 0.5; // 方块的一半边长
        // 获取偏移方向
        double offsetX = side.getOffsetX() * halfSize;
        double offsetY = side.getOffsetY() * halfSize;
        double offsetZ = side.getOffsetZ() * halfSize;
        // 面的中心点
        Vec3d faceCenter = center.add(offsetX, offsetY, offsetZ);
        points.add(faceCenter);
        // 面的四个角
        if (side.getAxis() == Direction.Axis.Y) { // 顶部/底部面
            points.add(faceCenter.add(halfSize, 0, halfSize));
            points.add(faceCenter.add(halfSize, 0, -halfSize));
            points.add(faceCenter.add(-halfSize, 0, halfSize));
            points.add(faceCenter.add(-halfSize, 0, -halfSize));
        } else if (side.getAxis() == Direction.Axis.X) { // 左/右面
            points.add(faceCenter.add(0, halfSize, halfSize));
            points.add(faceCenter.add(0, halfSize, -halfSize));
            points.add(faceCenter.add(0, -halfSize, halfSize));
            points.add(faceCenter.add(0, -halfSize, -halfSize));
        } else if (side.getAxis() == Direction.Axis.Z) { // 前/后面
            points.add(faceCenter.add(halfSize, halfSize, 0));
            points.add(faceCenter.add(halfSize, -halfSize, 0));
            points.add(faceCenter.add(-halfSize, halfSize, 0));
            points.add(faceCenter.add(-halfSize, -halfSize, 0));
        }
        return points;
    }

    public static double getPlayerBlockInteractionRange() {
        // double reachDistance = client.interactionManager.getReachDistance();
        return player.getBlockInteractionRange();
    }

}
