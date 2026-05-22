package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.util.NetworkUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ClientLevel.class, priority = 1010)
public abstract class ClientLevelMixin implements NetworkUtils.SequenceExtension {
    //#if MC > 11802
    @Final
    @Shadow
    private BlockStatePredictionHandler blockStatePredictionHandler;

    @Override
    public int fabric_bedrock_miner$getSequence() {
        try (BlockStatePredictionHandler pendingUpdateManager = this.blockStatePredictionHandler.startPredicting()) {
            return pendingUpdateManager.currentSequence();
        }
    }
    //#endif
}
