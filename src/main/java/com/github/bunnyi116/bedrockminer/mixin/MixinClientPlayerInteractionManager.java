package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.github.bunnyi116.bedrockminer.util.player.PlayerInteractionUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.world;

@Mixin(value = ClientPlayerInteractionManager.class, priority = 999)
public abstract class MixinClientPlayerInteractionManager {
    @Unique
    private int interactBlockCooldown = 0;

    @Inject(at = @At(value = "HEAD"), method = "attackBlock", cancellable = true)
    private void attackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BlockState blockState = world.getBlockState(pos);
        Block block = blockState.getBlock();
        if (TaskManager.getInstance().isBedrockMinerFeatureEnable()) {
            TaskManager.getInstance().addBlockTask(world, pos, block);
        }
        if (PlayerInteractionUtils.isBreakingBlock()) {
            cir.cancel();
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "cancelBlockBreaking", cancellable = true)
    public void cancelBlockBreaking(CallbackInfo ci) {
        if (PlayerInteractionUtils.isBreakingBlock()) {
            ci.cancel();
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "interactBlock", cancellable = true)
    private void interactBlock(ClientPlayerEntity player, Hand hand, BlockHitResult blockHitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (this.interactBlockCooldown > 0) {
            this.interactBlockCooldown--;
        } else {
            this.interactBlockCooldown = 1;
            BlockPos blockPos = blockHitResult.getBlockPos();
            BlockState blockState = world.getBlockState(blockPos);
            Block block = blockState.getBlock();
            if (TaskManager.getInstance().isBedrockMinerFeatureEnable() && player.getMainHandStack().isEmpty()) {
                TaskManager.getInstance().switchToggle(block);
            }
        }
        if (PlayerInteractionUtils.isBreakingBlock()) {
            cir.setReturnValue(ActionResult.FAIL);
            cir.cancel();
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "tick", cancellable = true)
    public void tick(CallbackInfo ci) {
        updateGameVariable();
        if (PlayerInteractionUtils.isBreakingBlock()) {
            ci.cancel();
        }
        TaskManager.getInstance().tick();
        PlayerInteractionUtils.autoResetBreaking();    // 自动解除拦截玩家破坏机制，避免任务阻塞或玩家离开任务方块破坏范围
    }

    @Unique
    private void updateGameVariable() {
        BedrockMiner.client = MinecraftClient.getInstance();
        world = BedrockMiner.client.world;
        BedrockMiner.player = BedrockMiner.client.player;
        if (BedrockMiner.player != null) {
            BedrockMiner.playerInventory = BedrockMiner.player.getInventory();
        }
        BedrockMiner.crosshairTarget = BedrockMiner.client.crosshairTarget;
        BedrockMiner.interactionManager = BedrockMiner.client.interactionManager;
        BedrockMiner.networkHandler = BedrockMiner.client.getNetworkHandler();
        if (BedrockMiner.interactionManager != null) {
            BedrockMiner.gameMode = BedrockMiner.interactionManager.getCurrentGameMode();
        }
    }
}
