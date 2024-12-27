package yan.lx.bedrockminer;

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
import yan.lx.bedrockminer.command.CommandManager;

public class BedrockMiner implements ModInitializer {
    public static final String MOD_NAME = "Bedrock Miner";
    public static final String MOD_ID = "bedrockminer";
    public static final String COMMAND_PREFIX = "bedrockMiner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final boolean TEST = true;

    // 常用游戏变量(通过 mixin 从 ClientPlayerInteractionManager 更新)
    public static MinecraftClient mc;
    public static ClientWorld world;
    public static ClientPlayerEntity player;
    public static PlayerInventory playerInventory;
    public static @Nullable HitResult crosshairTarget;
    public static ClientPlayNetworkHandler networkHandler;
    public static ClientPlayerInteractionManager interactionManager;
    public static GameMode gameMode;

    @Override
    public void onInitialize() {
        initGameVariable();
        CommandManager.init();
        Debug.alwaysWrite("模组初始化成功");
    }

    private static void initGameVariable() {
        var mc = MinecraftClient.getInstance();
        BedrockMiner.mc = mc;
        BedrockMiner.world = mc.world;
        BedrockMiner.player = mc.player;
        if (mc.player != null) {
            BedrockMiner.playerInventory = mc.player.getInventory();
        }
        BedrockMiner.crosshairTarget = mc.crosshairTarget;
        BedrockMiner.networkHandler = mc.getNetworkHandler();
        BedrockMiner.interactionManager = mc.interactionManager;
        if (mc.interactionManager != null) {
            BedrockMiner.gameMode = mc.interactionManager.getCurrentGameMode();
        }
    }
}
