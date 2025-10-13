package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

public class BlockPlacerUtils {
    public static void placement(BlockPos blockPos, Direction facing, @Nullable Item... items) {
        if (blockPos == null || facing == null)
            return;

        if (!world.getBlockState(blockPos).isReplaceable())
            return;

        if (!InteractionUtils.isBlockWithinReach(blockPos, facing, 1F)) {
            return;
        }
        if (items != null) {
            InventoryManagerUtils.switchToItem(items);
        }

        // 发送修改视角数据包
        if (facing.getAxis().isVertical()) {
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
            networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), false));
        }

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

    public static boolean canPlace(ClientWorld world, BlockPos blockPos, BlockState placeBlockState) {
        // 目标位置的方块是否可以被替换
        if (!world.getBlockState(blockPos).isReplaceable()) {
            return false;
        }
        // 检查放置方块的碰撞体积
        var collisionShape = placeBlockState.getCollisionShape(world, blockPos);
        if (collisionShape.isEmpty()) {
            return true; // 放置的方块是没有没有碰撞体积，可以放置
        }
        for (var entity : world.getEntities()) {
            if (entity instanceof ItemEntity){
                return true;
            }
            if (entity.collidesWithStateAtPos(blockPos, placeBlockState)) {
                return false;
            }
        }
        return true;
    }
}
