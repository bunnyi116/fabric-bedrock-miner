package com.github.bunnyi116.bedrockminer;

import com.github.bunnyi116.bedrockminer.command.CommandManager;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BedrockMiner implements ModInitializer  {
    public static final String MOD_NAME = "Bedrock Miner";
    public static final String MOD_ID = "bedrockminer";
    public static final String COMMAND_PREFIX = "bedrockMiner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final boolean TEST = false;

    // 常用游戏变量(通过 mixin 从 MultiPlayerGameMode 更新)
    public static Minecraft minecraft;
    public static ClientLevel level;
    public static LocalPlayer player;
    public static Inventory playerInventory;
    public static @Nullable HitResult hitResult;
    public static ClientPacketListener connection;
    public static MultiPlayerGameMode gameMode;
    public static GameType gameType;

    @Override
    public void onInitialize() {
        initGameVariable();
        CommandManager.register();
        Debug.alwaysWrite("模组初始化成功");
    }

    public static void initGameVariable() {
        Minecraft mc = Minecraft.getInstance();
        BedrockMiner.minecraft = mc;
        BedrockMiner.level = mc.level;
        BedrockMiner.player = mc.player;
        if (mc.player != null) {
            BedrockMiner.playerInventory = mc.player.getInventory();
        }
        BedrockMiner.hitResult = mc.hitResult;
        BedrockMiner.connection = mc.getConnection();
        BedrockMiner.gameMode = mc.gameMode;
        if (mc.gameMode!= null) {
            BedrockMiner.gameType = mc.gameMode.getPlayerMode();
        }
    }

    public static boolean gameVariableIsValid() {
        return minecraft != null
                && level != null
                && player != null
                && connection != null
                && gameMode != null;
    }
}
