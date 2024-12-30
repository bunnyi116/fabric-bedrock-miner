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

    public static boolean updateBlockBreakingProgress(BlockPos blockPos) {
        return updateBlockBreakingProgress(blockPos, InteractionUtils.getClosestFace(blockPos));
    }

    private static boolean updateBlockBreakingProgress(BlockPos blockPos, Direction direction) {
        if (!InteractionUtils.isBlockWithinReach(blockPos, direction)) {
            return false;
        }
        interactionManager.updateBlockBreakingProgress(blockPos, direction);
        return world.getBlockState(blockPos).isReplaceable();
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
        if (!InteractionUtils.isBlockWithinReach(blockPos, direction)) {
            return false;
        }
        interactionManager.attackBlock(blockPos, direction);
        return world.getBlockState(blockPos).isReplaceable();
    }
}
