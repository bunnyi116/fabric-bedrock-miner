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
    private static @Nullable Consumer<BlockPos> beforeBlockDestroyPacket;

    private static void extracted(BlockPos blockPos) {
        if (beforeBlockDestroyPacket != null) {
            beforeBlockDestroyPacket.accept(blockPos);
            beforeBlockDestroyPacket = null;
        }
    }

    public static boolean updateBlockBreakingProgress(BlockPos blockPos) {
        return updateBlockBreakingProgress(blockPos, InteractionUtils.getClosestFace(blockPos));
    }

    public static boolean attackBlock(BlockPos blockPos) {
        return attackBlock(blockPos, InteractionUtils.getClosestFace(blockPos));
    }

    public static boolean attackBlock(BlockPos pos, Direction direction) {
        if (player.isBlockBreakingRestricted(world, pos, gameMode)) {
            return false;
        } else if (!world.getWorldBorder().contains(pos)) {
            return false;
        } else {
            if (gameMode.isCreative()) {
                BlockState blockState = world.getBlockState(pos);
                mc.getTutorialManager().onBlockBreaking(world, pos, blockState, 1.0F);
                extracted(pos);
                interactionManager.sendSequencedPacket(world, (sequence) -> {
                    interactionManager.breakBlock(pos);
                    return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                });
            } else if (!interactionManager.breakingBlock || !isCurrentlyBreaking(pos)) {
                if (interactionManager.breakingBlock) {
                    networkHandler.sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, interactionManager.currentBreakingPos, direction));
                }

                BlockState blockState = world.getBlockState(pos);
                mc.getTutorialManager().onBlockBreaking(world, pos, blockState, 0.0F);
                interactionManager.sendSequencedPacket(world, (sequence) -> {
                    boolean bl = !blockState.isAir();
                    if (bl && interactionManager.currentBreakingProgress == 0.0F) {
                        blockState.onBlockBreakStart(world, pos, player);
                    }

                    if (bl && blockState.calcBlockBreakingDelta(player, player.getWorld(), pos) >= 1.0F) {
                        interactionManager.breakBlock(pos);
                    } else {
                        interactionManager.breakingBlock = true;
                        interactionManager.currentBreakingPos = pos;
                        interactionManager.selectedStack = player.getMainHandStack();
                        interactionManager.currentBreakingProgress = 0.0F;
                        interactionManager.blockBreakingSoundCooldown = 0.0F;
                        world.setBlockBreakingInfo(player.getId(), interactionManager.currentBreakingPos, interactionManager.getBlockBreakingProgress());
                    }

                    return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
                });
            }

            return true;
        }
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos, Direction direction) {
        interactionManager.syncSelectedSlot();
        if (gameMode.isCreative() && world.getWorldBorder().contains(pos)) {
            BlockState blockState = world.getBlockState(pos);
            mc.getTutorialManager().onBlockBreaking(world, pos, blockState, 1.0F);
            extracted(pos);
            interactionManager.sendSequencedPacket(world, (sequence) -> {
                interactionManager.breakBlock(pos);
                return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, direction, sequence);
            });
            return true;
        } else if (isCurrentlyBreaking(pos)) {
            BlockState blockState = world.getBlockState(pos);
            if (blockState.isAir()) {
                interactionManager.breakingBlock = false;
                return false;
            } else {
                interactionManager.currentBreakingProgress += blockState.calcBlockBreakingDelta(player, player.getWorld(), pos);
                if (interactionManager.blockBreakingSoundCooldown % 4.0F == 0.0F) {
                    BlockSoundGroup blockSoundGroup = blockState.getSoundGroup();
                    mc.getSoundManager().play(new PositionedSoundInstance(blockSoundGroup.getHitSound(), SoundCategory.BLOCKS, (blockSoundGroup.getVolume() + 1.0F) / 8.0F, blockSoundGroup.getPitch() * 0.5F, SoundInstance.createRandom(), pos));
                }

                ++interactionManager.blockBreakingSoundCooldown;
                mc.getTutorialManager().onBlockBreaking(world, pos, blockState, MathHelper.clamp(interactionManager.currentBreakingProgress, 0.0F, 1.0F));
                if (interactionManager.currentBreakingProgress >= 1.0F) {
                    interactionManager.breakingBlock = false;
                    extracted(pos);
                    interactionManager.sendSequencedPacket(world, (sequence) -> {
                        interactionManager.breakBlock(pos);
                        return new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                    });
                    interactionManager.currentBreakingProgress = 0.0F;
                    interactionManager.blockBreakingSoundCooldown = 0.0F;
                }

                world.setBlockBreakingInfo(player.getId(), interactionManager.currentBreakingPos, interactionManager.getBlockBreakingProgress());
                return true;
            }
        } else {
            return attackBlock(pos, direction);
        }
    }

    private static boolean isCurrentlyBreaking(BlockPos pos) {
        return pos.equals(interactionManager.currentBreakingPos);
    }


}
