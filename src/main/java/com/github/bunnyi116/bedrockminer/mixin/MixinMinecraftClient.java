package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.github.bunnyi116.bedrockminer.util.player.PlayerInteractionUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = MinecraftClient.class, priority = 999)
public abstract class MixinMinecraftClient {
    @Shadow
    @Nullable
    public ClientWorld world;

    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    @Shadow
    @Nullable
    public HitResult crosshairTarget;

    @Shadow
    @Nullable
    public ClientPlayerInteractionManager interactionManager;

    @Shadow
    @Nullable
    public abstract ClientPlayNetworkHandler getNetworkHandler();

    @Inject(method = "doItemUse", at = @At(value = "HEAD"))
    private void doItemUse(CallbackInfo ci) {
        if (crosshairTarget == null || world == null || player == null) {
            return;
        }
        if (crosshairTarget.getType() != HitResult.Type.BLOCK || !player.getMainHandStack().isEmpty()) {
            return;
        }
        var blockHitResult = (BlockHitResult) crosshairTarget;
        var blockPos = blockHitResult.getBlockPos();
        var blockState = world.getBlockState(blockPos);
        var block = blockState.getBlock();
        if (TaskManager.getInstance().isBedrockMinerFeatureEnable()) {
            TaskManager.getInstance().switchToggle(block);
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;updateBlockBreakingProgress(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z"), locals = LocalCapture.CAPTURE_FAILSOFT, cancellable = true)
    private void handleBlockBreaking(boolean bl, CallbackInfo ci, BlockHitResult blockHitResult, BlockPos blockPos, Direction direction) {
        if (world == null || player == null) {
            return;
        }
        var blockState = world.getBlockState(blockPos);
        var block = blockState.getBlock();
        if (TaskManager.getInstance().isBedrockMinerFeatureEnable()) {
            TaskManager.getInstance().addBlockTask(world, blockPos, block);
        }
        if (TaskManager.getInstance().isProcessing() || PlayerInteractionUtils.isBreakingBlock()) {    // 避免冲突, 当模组正在破坏时, 拦截玩家破坏操作
            ci.cancel();
        }
    }

    @Inject(method = "handleInputEvents", at = @At(value = "HEAD"))
    public void tick(CallbackInfo ci) {
        updateGameVariable();
        TaskManager.getInstance().tick();
        PlayerInteractionUtils.autoResetBreaking();    // 自动解除拦截玩家破坏机制，避免任务阻塞或玩家离开任务方块破坏范围
    }

    @Unique
    private void updateGameVariable() {
        BedrockMiner.client = (MinecraftClient) (Object) this;
        BedrockMiner.world = BedrockMiner.client.world;
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
