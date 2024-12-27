package yan.lx.bedrockminer.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yan.lx.bedrockminer.BedrockMiner;
import yan.lx.bedrockminer.task.TaskManager;

@Mixin(value = ClientPlayerInteractionManager.class, priority = 999)
public class MixinClientPlayerInteractionManager {
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private ClientPlayNetworkHandler networkHandler;

    @Shadow
    private GameMode gameMode;

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) {
        updateGameVariable();
        TaskManager.tick();
    }

    @Unique
    private void updateGameVariable() {
        BedrockMiner.mc = client;
        BedrockMiner.world = client.world;
        BedrockMiner.player = client.player;
        if (client.player != null) {
            BedrockMiner.playerInventory = client.player.getInventory();
        }
        BedrockMiner.crosshairTarget = client.crosshairTarget;
        BedrockMiner.interactionManager = (ClientPlayerInteractionManager) (Object) this;
        BedrockMiner.networkHandler = networkHandler;
        BedrockMiner.gameMode = gameMode;
    }
}
