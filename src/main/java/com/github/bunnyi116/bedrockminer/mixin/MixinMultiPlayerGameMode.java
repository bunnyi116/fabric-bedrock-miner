package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.github.bunnyi116.bedrockminer.util.InteractionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.world;

@Mixin(value = MultiPlayerGameMode.class, priority = 1010)
public abstract class MixinMultiPlayerGameMode {
    @Unique
    private int interactBlockCooldown = 0;

    @Inject(at = @At(value = "HEAD"), method = "startDestroyBlock", cancellable = true)
    private void attackBlock(BlockPos blockPos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (TaskManager.isWorking()) {
            BlockState blockState = world.getBlockState(blockPos);
            Block block = blockState.getBlock();
            if (TaskManager.getInstance().isBedrockMinerFeatureEnable()) {
                TaskManager.getInstance().addBlockTask(world, blockPos, block);
            }
            if (InteractionUtils.isBreakingBlock()) {
                cir.cancel();
            }
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "stopDestroyBlock", cancellable = true)
    public void cancelBlockBreaking(CallbackInfo ci) {
        if (TaskManager.isWorking() && InteractionUtils.isBreakingBlock()) {
            ci.cancel();
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "useItemOn", cancellable = true)
    private void interactBlock(LocalPlayer localPlayer, InteractionHand interactionHand, BlockHitResult blockHitResult, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos blockPos = blockHitResult.getBlockPos();
        BlockState blockState = world.getBlockState(blockPos);
        Block block = blockState.getBlock();
        if (interactBlockCooldown > 0) {
            interactBlockCooldown--;
            return;
        }
        interactBlockCooldown = 1;
        if (TaskManager.getInstance().isBedrockMinerFeatureEnable() && player.getMainHandItem().isEmpty() && !Config.getInstance().disableEmptyHandSwitchToggle) {
            if (InteractionUtils.isBreakingBlock()) {
                cir.setReturnValue(InteractionResult.FAIL);
                cir.cancel();
            }
            TaskManager.getInstance().switchToggle(block);
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "tick", cancellable = true)
    public void tick(CallbackInfo ci) {
        updateGameVariable();
        if (TaskManager.isWorking()) {
            if (InteractionUtils.isBreakingBlock()) {
                ci.cancel();
            }
            TaskManager.getInstance().tick();
        }
        InteractionUtils.autoResetBreaking();    // 自动解除拦截玩家破坏机制，避免任务阻塞或玩家离开任务方块破坏范围
    }

    @Unique
    private void updateGameVariable() {
        BedrockMiner.client = Minecraft.getInstance();
        world = BedrockMiner.client.level;
        player = BedrockMiner.client.player;
        if (player != null) {
            BedrockMiner.playerInventory = player.getInventory();
        }
        BedrockMiner.crosshairTarget = BedrockMiner.client.hitResult;
        BedrockMiner.interactionManager = BedrockMiner.client.gameMode;
        BedrockMiner.networkHandler = BedrockMiner.client.getConnection();
        if (BedrockMiner.interactionManager != null) {
            BedrockMiner.gameMode = BedrockMiner.interactionManager.getPlayerMode();
        }
    }
}
