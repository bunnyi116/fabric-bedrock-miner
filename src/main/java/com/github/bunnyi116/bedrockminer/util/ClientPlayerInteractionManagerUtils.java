package com.github.bunnyi116.bedrockminer.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.github.bunnyi116.bedrockminer.Mod.*;

@Environment(EnvType.CLIENT)
public class ClientPlayerInteractionManagerUtils {
    private static final float BREAKING_PROGRESS_MAX = 0.7F;    // 破坏进度最大值, 在原版中为1.0F, 但在服务端处理时, 使用的是0.7F
    private static BlockPos currentBreakingPos = new BlockPos(-1, -1, -1);
    private static ItemStack selectedStack;
    private static float currentBreakingProgress;
    private static float blockBreakingSoundCooldown;
    private static boolean breakingBlock;
    private static int lastSelectedSlot;
    private static int breakingTicks;
    private static int breakingTickMax;

    private static void syncSelectedSlot() {
        int slot = player.getInventory().getSelectedSlot();
        if (slot != lastSelectedSlot) {
            lastSelectedSlot = slot;
            networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(lastSelectedSlot));
        }
    }

    private static boolean isCurrentlyBreaking(BlockPos pos) {
        ItemStack itemStack = player.getMainHandStack();
        return pos.equals(currentBreakingPos) && ItemStack.areItemsAndComponentsEqual(itemStack, selectedStack);
    }

    private static int getBlockBreakingProgress() {
        return currentBreakingProgress > 0.0F ? (int) (currentBreakingProgress * 10.0F) : -1;
    }

    private static boolean attackBlock(BlockPos pos, Direction direction, @Nullable Runnable beforeBreaking, @Nullable Runnable afterBreaking) {
        if (player.isBlockBreakingRestricted(world, pos, gameMode)) {   // 玩家是否被限制破坏方块
            return false;
        } else if (!world.getWorldBorder().contains(pos)) { // 是否在世界边界内
            return false;
        } else {
            if (gameMode.isCreative()) {
                BlockState blockState = world.getBlockState(pos);
                client.getTutorialManager().onBlockBreaking(world, pos, blockState, 1.0F);
                sendSequencedPacket((sequence) -> { // 当处于创造模式时, 直接破坏方块
                    interactionManager.breakBlock(pos);
                    return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                }, beforeBreaking, afterBreaking);
            } else if (!breakingBlock || !isCurrentlyBreaking(pos)) {
                if (breakingBlock) { // 如果之前正在破坏方块, 但现在选择了新的方块, 则发送中止破坏的包
                    networkHandler.sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, currentBreakingPos, direction));
                    breakingBlock = false;
                }
                BlockState blockState = world.getBlockState(pos);
                client.getTutorialManager().onBlockBreaking(world, pos, blockState, 0.0F);
                var calcBlockBreakingDelta = blockState.calcBlockBreakingDelta(player, player.getWorld(), pos);
                if (calcBlockBreakingDelta >= BREAKING_PROGRESS_MAX) {
                    sendSequencedPacket((sequence) -> {
                        if (!blockState.isAir()) {
                            interactionManager.breakBlock(pos);
                        }
                        return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                    }, beforeBreaking, afterBreaking);
                } else {
                    sendSequencedPacket((sequence) -> {
                        if (!blockState.isAir() && currentBreakingProgress == 0.0F) {
                            blockState.onBlockBreakStart(world, pos, player);
                        }
                        breakingBlock = true;
                        currentBreakingPos = pos;
                        selectedStack = player.getMainHandStack();
                        currentBreakingProgress = 0.0F;
                        blockBreakingSoundCooldown = 0.0F;
                        world.setBlockBreakingInfo(player.getId(), currentBreakingPos, getBlockBreakingProgress());
                        return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                    });
                }
            }
            ++breakingTickMax;
            return true;
        }
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos, Direction direction, @Nullable Runnable beforeBreaking, @Nullable Runnable afterBreaking) {
        syncSelectedSlot();
        if (gameMode.isCreative() && world.getWorldBorder().contains(pos)) {
            BlockState blockState = world.getBlockState(pos);
            client.getTutorialManager().onBlockBreaking(world, pos, blockState, 1.0F);
            sendSequencedPacket((sequence) -> {
                interactionManager.breakBlock(pos);
                return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
            }, beforeBreaking, afterBreaking);
            breakingBlock = false;
            ++breakingTickMax;
            return true;
        } else if (breakingBlock && isCurrentlyBreaking(pos)) {
            BlockState blockState = world.getBlockState(pos);
            if (blockState.isAir()) {
                breakingBlock = false;
                return false;
            } else {
                currentBreakingProgress += blockState.calcBlockBreakingDelta(player, player.getWorld(), pos);
                if (blockBreakingSoundCooldown % 4.0F == 0.0F) {
                    BlockSoundGroup blockSoundGroup = blockState.getSoundGroup();
                    client.getSoundManager().play(new PositionedSoundInstance(blockSoundGroup.getHitSound(), SoundCategory.BLOCKS, (blockSoundGroup.getVolume() + 1.0F) / 8.0F, blockSoundGroup.getPitch() * 0.5F, SoundInstance.createRandom(), pos));
                }
                ++blockBreakingSoundCooldown;
                if (currentBreakingProgress >= BREAKING_PROGRESS_MAX) {
                    client.getTutorialManager().onBlockBreaking(world, pos, blockState, 1.0F);
                } else {
                    client.getTutorialManager().onBlockBreaking(world, pos, blockState, MathHelper.clamp(currentBreakingProgress, 0.0F, 1.0F));
                }
                if (currentBreakingProgress >= BREAKING_PROGRESS_MAX) {
                    breakingBlock = false;
                    sendSequencedPacket((sequence) -> {
                        interactionManager.breakBlock(pos);
                        return new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                    }, beforeBreaking, afterBreaking);
                    currentBreakingProgress = 0.0F;
                    blockBreakingSoundCooldown = 0.0F;
                }
                world.setBlockBreakingInfo(player.getId(), currentBreakingPos, getBlockBreakingProgress());
                ++breakingTickMax;
                return true;
            }
        } else {
            return attackBlock(pos, direction, beforeBreaking, afterBreaking);
        }
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos) {
        return updateBlockBreakingProgress(pos, ClientPlayerInteractionManagerUtils.getClosestFace(pos), null, null);
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator, @Nullable Runnable beforeSending, @Nullable Runnable afterSending) {
        try (PendingUpdateManager pendingUpdateManager = world.getPendingUpdateManager().incrementSequence()) {
            int i = pendingUpdateManager.getSequence();
            Packet<ServerPlayPacketListener> packet = packetCreator.predict(i);
            if (beforeSending != null) {
                beforeSending.run();    // 在发送数据包前执行
            }
            networkHandler.sendPacket(packet);
            if (afterSending != null) {
                afterSending.run(); // 在发送数据包后执行
            }
        }
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        sendSequencedPacket(packetCreator, null, null);
    }

    public static void resetBreaking() {
        breakingTicks = 0;
        breakingTickMax = 400;  // 20TICK=1秒, 400TICK=20秒
        breakingBlock = false;
    }

    public static void autoResetBreaking() {
        if (!breakingBlock && breakingTicks > 0) {  // 如果未在破坏, 但是破坏TICK已有累计, 先进行初始化
            resetBreaking();
        }
        if (breakingBlock && breakingTicks++ > breakingTickMax) {
            resetBreaking();
        }
    }

    public static boolean isBreakingBlock() {
        return breakingBlock;
    }

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

        if (!ClientPlayerInteractionManagerUtils.isBlockWithinReach(blockPos, facing, 1F)) {
            return;
        }
        if (items != null) {
            PlayerInventoryManagerUtils.switchToItem(items);
        }

        // 发送修改视角数据包
        networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), false));

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


    public static boolean canPlace(BlockPos targetPos, BlockState targetState) {
        if (!targetState.canPlaceAt(world, targetPos)) return false;
        BlockState blockState = world.getBlockState(targetPos);
        if (!blockState.isAir()) return false;
        if (!blockState.isReplaceable()) return false;
        if (!world.canPlace(targetState, targetPos, ShapeContext.absent())) return false;
        // 检查实体阻挡
        VoxelShape blockShape = targetState.getCollisionShape(world, targetPos);
        Box detectionArea = blockShape.getBoundingBox().offset(targetPos);
        List<Entity> entities = world.getOtherEntities(null, detectionArea);
        for (Entity entity : entities) {
            // 1. 排除无碰撞实体
            if (!entity.isCollidable()) continue;
            // 排除特定实体类型
            EntityType<?> type = entity.getType();
            if (type == EntityType.EXPERIENCE_ORB ||
                    type == EntityType.ITEM ||
                    type == EntityType.ARROW) {
                continue;
            }
            // 动态碰撞检测（可选）
            if (entity instanceof LivingEntity) {
                // 活体生物（玩家、怪物）始终检测
                VoxelShape entityShape = VoxelShapes.cuboid(entity.getBoundingBox());
                if (VoxelShapes.matchesAnywhere(blockShape, entityShape, BooleanBiFunction.AND)) {
                    return false;
                }
            }
        }
        return true;
    }



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
        return isBlockWithinReach(targetPos, getClosestFace(targetPos), 0);
    }

    public static boolean isBlockWithinReach(BlockPos targetPos, double deltaReachDistance) {
        return isBlockWithinReach(targetPos, getClosestFace(targetPos), deltaReachDistance);
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
