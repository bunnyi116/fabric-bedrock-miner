package yan.lx.bedrockminer.utils;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import static yan.lx.bedrockminer.BedrockMiner.*;

public class BlockBreakerUtils {
    private static BlockPos currentBreakingPos = new BlockPos(-1, -1, -1);
    private static float currentBreakingProgress;
    private static float blockBreakingSoundCooldown;
    private static boolean breakingBlock;
    private static @Nullable Consumer<BlockPos> beforeBlockDestroyPacket;

    public static boolean updateBlockBreakingProgress(BlockPos blockPos) {
        return updateBlockBreakingProgress(blockPos, InteractionUtils.getClosestFace(blockPos));
    }

    private static boolean updateBlockBreakingProgress(BlockPos blockPos, Direction direction) {
        if (!InteractionUtils.isBlockWithinReach(blockPos, direction)) {
            return false;
        }
        if (!world.getWorldBorder().contains(blockPos) || gameMode.isBlockBreakingRestricted()) {
            return false;
        }
        BlockState blockState = world.getBlockState(blockPos);
        if (gameMode.isCreative()) {    // 创造模式
            extracted(blockPos);
            mc.getTutorialManager().onBlockBreaking(world, blockPos, blockState, 1.0F);
            interactionManager.sendSequencedPacket(world, (sequence) -> {
                interactionManager.breakBlock(blockPos); // 执行方块破坏
                return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, blockPos, direction, sequence);
            });
            return true;
        } else if (isCurrentlyBreaking(blockPos)) { // 目标方块是否为当前破坏位置
            // 如果方块为空气，停止破坏
            if (blockState.isAir()) {
                breakingBlock = false;
                return false;
            } else {
                // 计算破坏进度
                currentBreakingProgress += blockState.calcBlockBreakingDelta(player, player.getWorld(), blockPos);
                // 每4次播放一次方块破坏声音
                if (blockBreakingSoundCooldown % 4.0F == 0.0F) {
                    BlockSoundGroup blockSoundGroup = blockState.getSoundGroup();
                    // 播放破坏声音
                    mc.getSoundManager().play(new PositionedSoundInstance(blockSoundGroup.getHitSound(), SoundCategory.BLOCKS, (blockSoundGroup.getVolume() + 1.0F) / 8.0F, blockSoundGroup.getPitch() * 0.5F, SoundInstance.createRandom(), blockPos));
                }

                // 增加声音冷却计数
                ++blockBreakingSoundCooldown;
                // 更新破坏进度到教程管理器
                mc.getTutorialManager().onBlockBreaking(world, blockPos, blockState, MathHelper.clamp(currentBreakingProgress, 0.0F, 1.0F));
                // 如果破坏进度达到100%，停止破坏
                if (currentBreakingProgress >= 1.0F) {
                    breakingBlock = false;
                    extracted(blockPos);
                    // 发送停止破坏的包
                    interactionManager.sendSequencedPacket(world, (sequence) -> {
                        interactionManager.breakBlock(blockPos);
                        return new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, blockPos, direction, sequence);
                    });
                    // 重置进度和冷却
                    currentBreakingProgress = 0.0F;
                    blockBreakingSoundCooldown = 0.0F;
                }
                // 更新方块破坏信息
                world.setBlockBreakingInfo(player.getId(), currentBreakingPos, getBlockBreakingProgress());
                return true;
            }
        } else {
            return attackBlock(blockPos, direction);
        }
    }

    private static void extracted(BlockPos blockPos) {
        if (beforeBlockDestroyPacket != null) {
            beforeBlockDestroyPacket.accept(blockPos);
            beforeBlockDestroyPacket = null;
        }
    }

    public static boolean attackBlock(BlockPos blockPos) {
        return attackBlock(blockPos, InteractionUtils.getClosestFace(blockPos));
    }

    public static boolean attackBlock(BlockPos blockPos, Direction direction) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (player == null || world == null || interactionManager == null || networkHandler == null || blockPos == null) {
            return false;
        }
        if (!InteractionUtils.isBlockWithinReach(blockPos, direction)) {
            return false;
        }
        GameMode gameMode = interactionManager.getCurrentGameMode();
        if (player.isBlockBreakingRestricted(world, blockPos, gameMode)) {
            return false;
        } else if (!world.getWorldBorder().contains(blockPos)) {
            return false;
        } else {
            if (gameMode.isCreative()) {
                extracted(blockPos);
                BlockState blockState = world.getBlockState(blockPos);
                client.getTutorialManager().onBlockBreaking(world, blockPos, blockState, 1.0F);
                interactionManager.sendSequencedPacket(world, (sequence) -> {
                    interactionManager.breakBlock(blockPos);
                    return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, blockPos, direction, sequence);
                });
            } else if (!breakingBlock || !isCurrentlyBreaking(blockPos)) {
                if (breakingBlock) {
                    networkHandler.sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, currentBreakingPos, direction));
                }
                BlockState blockState = world.getBlockState(blockPos);
                client.getTutorialManager().onBlockBreaking(world, blockPos, blockState, 0.0F);
                interactionManager.sendSequencedPacket(world, (sequence) -> {
                    boolean bl = !blockState.isAir();
                    if (bl && currentBreakingProgress == 0.0F) {
                        blockState.onBlockBreakStart(world, blockPos, player);
                    }
                    if (bl && blockState.calcBlockBreakingDelta(player, player.getWorld(), blockPos) >= 1.0F) {
                        interactionManager.breakBlock(blockPos);
                    } else {
                        breakingBlock = true;
                        currentBreakingPos = blockPos;
                        currentBreakingProgress = 0.0F;
                        blockBreakingSoundCooldown = 0.0F;
                        world.setBlockBreakingInfo(player.getId(), currentBreakingPos, getBlockBreakingProgress());
                    }
                    return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, blockPos, direction, sequence);
                });
            }
            return true;
        }
    }

    private static boolean isCurrentlyBreaking(BlockPos pos) {
        return pos.equals(currentBreakingPos);
    }

    private static int getBlockBreakingProgress() {
        return currentBreakingProgress > 0.0F ? (int) (currentBreakingProgress * 10.0F) : -1;
    }
}
