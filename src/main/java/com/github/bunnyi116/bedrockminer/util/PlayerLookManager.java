package com.github.bunnyi116.bedrockminer.util;

import com.github.bunnyi116.bedrockminer.task.Task;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.networkHandler;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;

public class PlayerLookManager {
    private static boolean modifyYaw = false;
    private static boolean modifyPitch = false;
    private static float yaw = 0F;
    private static float pitch = 0F;
    private static int ticks = 0;
    private static @Nullable Task task = null;

    public static float onModifyLookYaw(float yaw) {
        return modifyYaw ? PlayerLookManager.yaw : yaw;
    }

    public static float onModifyLookPitch(float pitch) {
        return modifyPitch ? PlayerLookManager.pitch : pitch;
    }

    private static PlayerMoveC2SPacket getLookAndOnGroundPacket(ClientPlayerEntity player) {
        var yaw = modifyYaw ? PlayerLookManager.yaw : player.getYaw();
        var pitch = modifyPitch ? PlayerLookManager.pitch : player.getPitch();
        return new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), false);
    }

    public static void sendLookAndOnGroundPacket() {
        if (networkHandler != null && player != null) {
            networkHandler.sendPacket(getLookAndOnGroundPacket(player));
        }
    }

    public static void set(float yaw, float pitch) {
        PlayerLookManager.modifyYaw = true;
        PlayerLookManager.yaw = yaw;
        PlayerLookManager.modifyPitch = true;
        PlayerLookManager.pitch = pitch;
    }

    public static void set(Direction facing, Task task) {
        PlayerLookManager.task = task;
        float yaw = switch (facing) {
            case SOUTH -> 180F;
            case EAST -> 90F;
            case NORTH -> 0F;
            case WEST -> -90F;
            default -> player == null ? 0F : player.getYaw();
        };
        float pitch = switch (facing) {
            case UP -> 90F;
            case DOWN -> -90F;
            default -> 0F;
        };
        set(yaw, pitch);
        sendLookAndOnGroundPacket();
    }

    public static void reset() {
        modifyYaw = false;
        yaw = 0F;
        modifyPitch = false;
        pitch = 0F;
        task = null;
        // 发送一个还原视角的数据包
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler != null && player != null) {
            networkHandler.sendPacket(getLookAndOnGroundPacket(player));
        }
    }

    public static void onTick() {
        if (isModify()) {   // 自动重置视角
            if (ticks++ > 20) {
                ticks = 0;
                reset();
            }
        }
    }

    public static boolean isModify() {
        return modifyYaw || modifyPitch;
    }

    public static @Nullable Task getTask() {
        return task;
    }
}
