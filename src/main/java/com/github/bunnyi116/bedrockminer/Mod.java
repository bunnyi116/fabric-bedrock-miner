package com.github.bunnyi116.bedrockminer;

import com.github.bunnyi116.bedrockminer.command.CommandManager;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mod implements ModInitializer {
    public static final String MOD_NAME = "Bedrock Miner";
    public static final String MOD_ID = "bedrockminer";
    public static final String COMMAND_PREFIX = "bedrockMiner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final boolean TEST = true;

    public static MinecraftClient client;
    public static ClientWorld world;
    public static ClientPlayerEntity player;
    public static PlayerInventory playerInventory;
    public static @Nullable HitResult crosshairTarget;
    public static ClientPlayNetworkHandler networkHandler;
    public static ClientPlayerInteractionManager interactionManager;
    public static GameMode gameMode;

    @Override
    public void onInitialize() {
        updateGameVariable();
        CommandManager.init();
        Debug.alwaysWrite("模组初始化成功");
    }

    public static void updateGameVariable() {
        var mc = MinecraftClient.getInstance();
        Mod.client = mc;
        Mod.world = mc.world;
        Mod.player = mc.player;
        if (mc.player == null) {
            Mod.playerInventory = null;
        } else {
            Mod.playerInventory = mc.player.getInventory();
        }
        Mod.crosshairTarget = mc.crosshairTarget;
        Mod.networkHandler = mc.getNetworkHandler();
        Mod.interactionManager = mc.interactionManager;
        if (mc.interactionManager == null) {
            Mod.gameMode = null;
        } else {
            Mod.gameMode = mc.interactionManager.getCurrentGameMode();
        }
    }
}
