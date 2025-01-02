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

        if (!InteractionUtils.isBlockWithinReach(blockPos, facing)) {
            return;
        }
        if (items != null) {
            InventoryManagerUtils.switchToItem(items);
        }

        // 发送修改视角数据包
        networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround()));

        // 模拟选中位置(凭空放置)
        var hitPos = blockPos.offset(facing.getOpposite());
        var hitVec3d = hitPos.toCenterPos().offset(facing, 0.5F);   // 放置面中心坐标
        var hitResult = new BlockHitResult(hitVec3d, facing, blockPos, false);

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
