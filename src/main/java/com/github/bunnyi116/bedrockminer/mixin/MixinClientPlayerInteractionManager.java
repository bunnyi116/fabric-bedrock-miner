package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.TaskManager2;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.github.bunnyi116.bedrockminer.util.player.PlayerInteractionUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = ClientPlayerInteractionManager.class, priority = 999)
public abstract class MixinClientPlayerInteractionManager {
    @Inject(method = "cancelBlockBreaking", at = @At(value = "TAIL"), cancellable = true)
    public void cancelBlockBreaking(CallbackInfo ci) {
        if (PlayerInteractionUtils.isBreakingBlock()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "HEAD"))
    public void tick(CallbackInfo ci) {
        updateGameVariable();
        if (PlayerInteractionUtils.isCancel()) {
            PlayerInteractionUtils.tick();
        } else {
            TaskManager.getInstance().tick();
        }
        PlayerInteractionUtils.autoResetBreaking();    // 自动解除拦截玩家破坏机制，避免任务阻塞或玩家离开任务方块破坏范围
    }

    @Unique
    private void updateGameVariable() {
        BedrockMiner.client = MinecraftClient.getInstance();
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
