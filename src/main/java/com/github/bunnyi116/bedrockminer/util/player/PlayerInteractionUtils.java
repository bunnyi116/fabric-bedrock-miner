package com.github.bunnyi116.bedrockminer.util.player;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.mixin.ClientPlayerInteractionManagerAccessor;
import com.github.bunnyi116.bedrockminer.util.block.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.network.NetworkUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;


@Environment(EnvType.CLIENT)
public class PlayerInteractionUtils {
    public static final float BREAKING_PROGRESS_MAX = 1.0F;
    private static boolean breakingBlock;
    private static int breakingTicks;
    private static int breakingTickMax;
    private static final Queue<BlockPos> blockQueue = new ArrayDeque<>();

    private static ClientPlayerInteractionManagerAccessor getAccessor() {
        return (ClientPlayerInteractionManagerAccessor) BedrockMiner.interactionManager;
    }

    public static boolean attackBlock(BlockPos pos, Direction direction, @Nullable Runnable packetSending, @Nullable Runnable packetSent) {
        breakingTicks = 0;
        ClientPlayerInteractionManagerAccessor accessor = getAccessor();
        if (accessor == null) return false;

        MinecraftClient client = accessor.getClient();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) return false;

        if (!pos.equals(accessor.getCurrentBreakingPos())) {
            blockQueue.add(accessor.getCurrentBreakingPos());
        }

        accessor.setBreakingBlock(false);   // 避免原版游戏TICK发送取消破坏数据包
        accessor.setBlockBreakingCooldown(0);
        if (player.isBlockBreakingRestricted(world, pos, accessor.getGameMode())) {
            return false;
        }
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }
        if (player.getAbilities().creativeMode) {
            setBreakingBlock(true);
            NetworkUtils.sendSequencedPacket(world, (sequence) -> {
                accessor.interactBreakBlock(pos);
                setBreakingBlock(false);
                return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
            }, packetSending, packetSent);
        } else if (!isBreakingBlock() || !accessor.interactIsCurrentlyBreaking(pos)) {
            if (isBreakingBlock()) {
                accessor.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, accessor.getCurrentBreakingPos(), direction));
            }
            NetworkUtils.sendSequencedPacket(world, (sequence) -> {
                BlockState blockState = world.getBlockState(pos);
                boolean bl = !blockState.isAir();
                if (bl && accessor.getCurrentBreakingProgress() == 0.0F) {
                    blockState.onBlockBreakStart(world, pos, player);
                }
                if (bl && PlayerUtils.calcBlockBreakingDelta(blockState) >= BREAKING_PROGRESS_MAX) {
                    accessor.interactBreakBlock(pos);
                    setBreakingBlock(false);
                } else {
                    accessor.setCurrentBreakingPos(pos);
                    accessor.setSelectedStack(player.getMainHandStack());
                    accessor.setCurrentBreakingProgress(0.0F);
                    accessor.setBlockBreakingSoundCooldown(0.0F);
                    world.setBlockBreakingInfo(player.getId(), accessor.getCurrentBreakingPos(), accessor.interactGetBlockBreakingProgress());
                    setBreakingBlock(true);
                }
                return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
            }, packetSending, packetSent);
        }
        return true;
    }

    public static boolean attackBlock(BlockPos pos) {
        return attackBlock(pos, PlayerUtils.getClosestFace(pos), null, null);
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos, Direction direction, @Nullable Runnable packetSending, @Nullable Runnable packetSent) {
        breakingTicks = 0;
        ClientPlayerInteractionManagerAccessor accessor = getAccessor();
        if (accessor == null) return false;

        MinecraftClient client = getAccessor().getClient();
        ClientPlayerEntity player = getAccessor().getClient().player;
        ClientWorld world = getAccessor().getClient().world;
        if (player == null || world == null) return false;

        if (!pos.equals(accessor.getCurrentBreakingPos())) {
            blockQueue.add(accessor.getCurrentBreakingPos());
        }

        accessor.setBreakingBlock(false);   // 避免原版游戏TICK发送取消破坏数据包
        accessor.setBlockBreakingCooldown(0);
        accessor.invokeSyncSelectedSlot();
        if (player.getAbilities().creativeMode && world.getWorldBorder().contains(pos)) {
            setBreakingBlock(true);
            NetworkUtils.sendSequencedPacket(world, (sequence) -> {
                accessor.interactBreakBlock(pos);
                setBreakingBlock(false);
                return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
            }, packetSending, packetSent);
            return true;
        }

        if (accessor.interactIsCurrentlyBreaking(pos)) {
            BlockState blockState = world.getBlockState(pos);
            if (blockState.isAir()) {
                setBreakingBlock(false);
                return false;
            } else {
                accessor.setCurrentBreakingProgress(accessor.getCurrentBreakingProgress() + PlayerUtils.calcBlockBreakingDelta(blockState));
                if (accessor.getBlockBreakingSoundCooldown() % 4.0F == 0.0F) {
                    BlockSoundGroup blockSoundGroup = blockState.getSoundGroup();
                    client.getSoundManager().play(new PositionedSoundInstance(blockSoundGroup.getHitSound(), SoundCategory.BLOCKS, (blockSoundGroup.getVolume() + 1.0F) / 8.0F, blockSoundGroup.getPitch() * 0.5F, SoundInstance.createRandom(), pos));
                }
                accessor.setBlockBreakingSoundCooldown(accessor.getBlockBreakingSoundCooldown() + 1);
                setBreakingBlock(true);
                if (accessor.getCurrentBreakingProgress() >= BREAKING_PROGRESS_MAX) {
                    NetworkUtils.sendSequencedPacket(world, (sequence) -> {
                        accessor.interactBreakBlock(pos);
                        setBreakingBlock(false);
                        return new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                    }, packetSending, packetSent);

                    accessor.setCurrentBreakingProgress(0.0F);
                    accessor.setBlockBreakingSoundCooldown(0.0F);
                    accessor.setBlockBreakingCooldown(0);
                }
                world.setBlockBreakingInfo(player.getId(), accessor.getCurrentBreakingPos(), accessor.interactGetBlockBreakingProgress());
                return true;
            }
        } else {
            return attackBlock(pos, direction, packetSending, packetSent);
        }
    }

    public static void updateBlockBreakingProgress(BlockPos pos, @Nullable Runnable packetSending, @Nullable Runnable packetSent) {
        updateBlockBreakingProgress(pos, PlayerUtils.getClosestFace(pos), packetSending, packetSent);
    }

    public static void updateBlockBreakingProgress(BlockPos pos) {
        updateBlockBreakingProgress(pos, PlayerUtils.getClosestFace(pos), null, null);
    }

    public static void resetBreaking() {
        breakingTicks = 0;
        breakingTickMax = 20;
        setBreakingBlock(false);
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

    public static void setBreakingBlock(boolean breakingBlock) {
        PlayerInteractionUtils.breakingBlock = breakingBlock;
    }


    public static void addBlockToQueue(BlockPos pos) {
        if (pos != null && !blockQueue.contains(pos)) {
            blockQueue.add(pos);
        }
    }

    /**
     * 批量添加方块到破坏队列
     */
    public static void addBlocksToQueue(List<BlockPos> posList) {
        for (BlockPos pos : posList) {
            addBlockToQueue(pos);
        }
    }

    /**
     * 清空破坏队列并停止当前破坏
     */
    public static void clearQueue() {
        blockQueue.clear();
        resetBreaking();
        // 此时如果正在破坏，应该发送 ABORT 包
        ClientPlayerInteractionManagerAccessor accessor = getAccessor();
        if (accessor != null && accessor.isBreakingBlock()) {
            accessor.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, accessor.getCurrentBreakingPos(), Direction.DOWN));
        }
    }

    /**
     * 返回当前队列的大小
     */
    public static int getQueueSize() {
        return blockQueue.size();
    }

    public static boolean isCancel() {
        return !blockQueue.isEmpty();
    }

    /**
     * 核心 Tick 方法
     * 应该在客户端 Tick 事件中调用此方法 (例如 ClientTickEvents.END_CLIENT_TICK)
     */
    public static void tick() {
        ClientPlayerInteractionManagerAccessor accessor = getAccessor();
        if (accessor == null) return;
        MinecraftClient client = getAccessor().getClient();
        if (client.player == null || client.world == null) {
            clearQueue();
            return;
        }
        int i = 0;
        Iterator<BlockPos> iterator = blockQueue.iterator();
        while (iterator.hasNext() && i++ < 10) {
            BlockPos currentQueuePos = iterator.next();
            if (currentQueuePos == null) continue;
            BlockState state = client.world.getBlockState(currentQueuePos);
            if (state.isOf(Blocks.MOVING_PISTON)) {
                return;
            }
            if (state.isAir() || state.getBlock().getHardness() < 0 || !PlayerUtils.canInteractWithBlockAt(currentQueuePos, 1F)) {
                iterator.remove();
            } else {
                if (PlayerUtils.canInteractWithBlockAt(currentQueuePos, 1f)) {
                    PlayerInventoryUtils.autoSwitch(state);
                    updateBlockBreakingProgress(currentQueuePos);
                    if (!PlayerUtils.canInstantlyMineBlock(state)) {
                        return;
                    }
                }
            }
        }
        if (blockQueue.isEmpty()) {
            autoResetBreaking();
        }
    }

    public static ActionResult interactBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, @Nullable Runnable beforeInteract, @Nullable Runnable afterInteract) {
        ClientPlayerInteractionManagerAccessor accessor = getAccessor();
        if (accessor == null) return ActionResult.FAIL;

        accessor.invokeSyncSelectedSlot();

        MinecraftClient client = accessor.getClient();
        if (!client.world.getWorldBorder().contains(hitResult.getBlockPos())) {
            return ActionResult.FAIL;
        }

        MutableObject<ActionResult> mutableObject = new MutableObject<>();

        NetworkUtils.sendSequencedPacket(client.world, (sequence) -> {
            mutableObject.setValue(accessor.interactInteractBlockInternal(player, hand, hitResult));
            return new PlayerInteractBlockC2SPacket(hand, hitResult, sequence);
        }, beforeInteract, afterInteract);

        return mutableObject.getValue();
    }


    public static ActionResult interactBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult) {
        return interactBlock(player, hand, hitResult, null, null);
    }


    public static void placement(BlockPos blockPos, Direction facing, @Nullable Runnable beforeInteract, @Nullable Runnable afterInteract, @Nullable Item... items) {
        if (blockPos == null || facing == null)
            return;

        if (!BlockUtils.isReplaceable(world.getBlockState(blockPos)))
            return;

        if (!PlayerUtils.canInteractWithBlockAt(blockPos, 1.0F)) {
            return;
        }
        if (items != null) {
            PlayerInventoryUtils.switchToItem(items);
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
            PlayerLookManager.sendLookPacket(networkHandler, yaw, pitch);
        }

        // 模拟选中位置(凭空放置)
        var hitPos = blockPos.offset(facing.getOpposite());
        var hitVec3d = Vec3d.ofCenter(hitPos).offset(facing, 0.5F);   // 放置面中心坐标
        var hitResult = new BlockHitResult(hitVec3d, facing, blockPos, false);

        // 使用新的 interactBlock 方法
        interactBlock(player, Hand.MAIN_HAND, hitResult, beforeInteract, afterInteract);
    }

    // 重载方法，保持向后兼容
    public static void placement(BlockPos blockPos, Direction facing, @Nullable Item... items) {
        placement(blockPos, facing, null, null, items);
    }

    public static void placement(BlockPos blockPos, Direction facing) {
        placement(blockPos, facing, (Item) null);
    }

    public static boolean canPlace(ClientWorld world, BlockPos blockPos, BlockState placeBlockState) {
        // 目标位置的方块是否可以被替换
        if (!BlockUtils.isReplaceable(world.getBlockState(blockPos))) {
            return false;
        }
        // 检查放置方块的碰撞体积
        var collisionShape = placeBlockState.getCollisionShape(world, blockPos);
        if (collisionShape.isEmpty()) {
            return true; // 放置的方块是没有没有碰撞体积，可以放置
        }
        for (var entity : world.getEntities()) {
            if (entity instanceof ItemEntity) {
                return true;
            }
            if (entity.collidesWithStateAtPos(blockPos, placeBlockState)) {
                return false;
            }
        }
        return true;
    }
}
