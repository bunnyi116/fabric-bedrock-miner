package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.level;

@Mixin(value = LocalPlayer.class, priority = 1010)
public abstract class LocalPlayerMixin {
    @Inject(at = @At(value = "HEAD"), method = "tick")
    public void tick(CallbackInfo ci) {
        updateGameVariable();
        if (TaskManager.getInstance().isRunning()) {
            TaskManager.getInstance().tick();
        }
    }

    @Unique
    private void updateGameVariable() {
        BedrockMiner.minecraft = Minecraft.getInstance();
        level = BedrockMiner.minecraft.level;
        player = BedrockMiner.minecraft.player;
        if (player != null) {
            BedrockMiner.playerInventory = player.getInventory();
        }
        BedrockMiner.hitResult = BedrockMiner.minecraft.hitResult;
        BedrockMiner.gameMode = BedrockMiner.minecraft.gameMode;
        BedrockMiner.connection = BedrockMiner.minecraft.getConnection();
        if (BedrockMiner.gameMode != null) {
            BedrockMiner.gameType = BedrockMiner.gameMode.getPlayerMode();
        }
    }
}
