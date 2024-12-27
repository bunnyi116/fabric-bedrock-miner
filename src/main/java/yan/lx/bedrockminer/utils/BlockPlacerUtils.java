package yan.lx.bedrockminer.utils;

import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import yan.lx.bedrockminer.Debug;

import static yan.lx.bedrockminer.BedrockMiner.*;

public class BlockPlacerUtils {
    public static final double MAX_BREAK_SQUARED_DISTANCE = MathHelper.square(6.0);

    /**
     * 活塞放置
     *
     * @param blockPos 活塞放置坐标
     * @param facing   活塞放置方向
     * @param items    使用的物品
     */
    public static void placement(BlockPos blockPos, Direction facing, @Nullable Item... items) {
        if (blockPos == null || facing == null) return;
        if (!world.getBlockState(blockPos).isReplaceable()) return;
        var yaw = switch (facing) {
            case SOUTH -> 180F;
            case EAST -> 90F;
            case NORTH -> 0F;
            case WEST -> -90F;
            default -> player.getYaw();
        };
        var pitch = switch (facing) {
            case UP -> 90F;
            case DOWN -> -90F;
            default -> 0F;
        };
        // 模拟选中位置(凭空放置)
        var blockCenterPos = Vec3d.ofCenter(blockPos);
        var hitPos = blockPos.offset(facing.getOpposite());
        var hitVec3d = hitPos.toCenterPos().offset(facing, 0.5F);   // 放置面中心坐标
        var hitResult = new BlockHitResult(hitVec3d, facing, blockPos, false);
        var distance = player.getEyePos().squaredDistanceTo(blockCenterPos);
        if (distance > MAX_BREAK_SQUARED_DISTANCE) {
            Debug.write("玩家位置离目标方块位置超过限制%s, 当前距离目标方块：%s", MAX_BREAK_SQUARED_DISTANCE, distance);
            return;
        }
        var spacing = hitVec3d.subtract(blockCenterPos); // 选中放置面与目标方块中心位置的间距
        var maxRange = 1.0000001D;
        if (!(Math.abs(spacing.getX()) < maxRange && Math.abs(spacing.getY()) < maxRange && Math.abs(spacing.getZ()) < maxRange)) {
            Debug.write("选中放置面与目标方块中心位置的间距超过限制%s！%s, %s", maxRange, blockPos.toShortString(), hitVec3d);
            return;
        }
        if (items != null) {
            InventoryManagerUtils.switchToItem(items);
        }

        // 发送修改视角数据包
        networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), true));
        // 发送交互方块数据包
        interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
    }

    public static void placement(BlockPos blockPos, Direction facing) {
        placement(blockPos, facing, (Item) null);
    }

    public static void simpleBlockPlacement(BlockPos blockPos) {
        simpleBlockPlacement(blockPos, (Item) null);
    }

    public static void simpleBlockPlacement(BlockPos blockPos, @Nullable Item... items) {
        placement(blockPos, Direction.UP, items);
    }
}
