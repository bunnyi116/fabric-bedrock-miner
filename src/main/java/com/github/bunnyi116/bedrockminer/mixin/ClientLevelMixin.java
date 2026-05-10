package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.github.bunnyi116.bedrockminer.util.InteractionUtils;
import com.github.bunnyi116.bedrockminer.util.NetworkUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.world;

@Mixin(value = ClientLevel.class, priority = 1010)
public abstract class ClientLevelMixin implements NetworkUtils.SequenceExtension {
    //#if MC > 11802
    @Final
    @Shadow
    private net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler blockStatePredictionHandler;

    @Override
    public int fabric_bedrock_miner$getSequence() {
        try (net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler pendingUpdateManager = this.blockStatePredictionHandler.startPredicting()) {
            return pendingUpdateManager.currentSequence();
        }
    }
    //#endif
}
