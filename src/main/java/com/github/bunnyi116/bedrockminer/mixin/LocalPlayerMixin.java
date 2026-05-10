package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.github.bunnyi116.bedrockminer.util.InteractionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.world;

@Mixin(value = LocalPlayer.class, priority = 1010)
public abstract class LocalPlayerMixin {
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
