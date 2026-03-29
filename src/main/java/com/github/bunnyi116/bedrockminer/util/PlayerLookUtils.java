package com.github.bunnyi116.bedrockminer.util;

import com.github.bunnyi116.bedrockminer.task.Task;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;

public class PlayerLookUtils {
    private static boolean modifyYaw = false;
    private static boolean modifyPitch = false;
    private static float yaw = 0F;
    private static float pitch = 0F;
    private static int ticks = 0;
    private static @Nullable Task task = null;

    public static float getYaw(float yaw) {
        return PlayerLookUtils.modifyYaw ? PlayerLookUtils.yaw : yaw;
    }

    public static float getPitch(float pitch) {
        return PlayerLookUtils.modifyPitch ? PlayerLookUtils.pitch : pitch;
    }

    public static Direction getPlacementDirection() {
        float currentYaw = getYaw(player != null ? player.getYRot() : 0F);
        float currentPitch = getPitch(player != null ? player.getXRot() : 0F);
        return DirectionUtils.orderedByNearest(currentYaw, currentPitch)[0].getOpposite();
    }

    public static ServerboundMovePlayerPacket getLookPacket(float yaw, float pitch) {
        //#if MC > 12101
        return new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), false);
        //#else
        //$$ return new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround());
        //#endif
    }

    public static void sendLookPacket(ServerboundMovePlayerPacket packet) {
        NetworkUtils.sendPacket(packet);
    }

    private static ServerboundMovePlayerPacket getLookPacket(LocalPlayer player) {
        float yaw = PlayerLookUtils.modifyYaw ? PlayerLookUtils.yaw : player.getYRot();
        float pitch = PlayerLookUtils.modifyPitch ? PlayerLookUtils.pitch : player.getXRot();
        return getLookPacket(yaw, pitch);
    }

    public static void sendLookPacket() {
        NetworkUtils.sendPacket(PlayerLookUtils.getLookPacket(player));
    }

    public static void sendLookPacket(float yaw, float pitch) {
        NetworkUtils.sendPacket(PlayerLookUtils.getLookPacket(yaw, pitch));
    }

    public static void setYaw(float yaw) {
        PlayerLookUtils.yaw = yaw;
        PlayerLookUtils.modifyYaw = true;
    }

    public static void setPitch(float pitch) {
        PlayerLookUtils.pitch = pitch;
        PlayerLookUtils.modifyPitch = true;
    }

    public static void set(float yaw, float pitch) {
        PlayerLookUtils.setYaw(yaw);
        PlayerLookUtils.setPitch(pitch);
    }

    public static void set(Direction facing, Task task) {
        PlayerLookUtils.task = task;
        float yaw;
        switch (facing) {
            case SOUTH:
                yaw = 180F;
                break;
            case EAST:
                yaw = 90F;
                break;
            case NORTH:
                yaw = 0F;
                break;
            case WEST:
                yaw = -90F;
                break;
            default:
                yaw = player == null ? 0F : player.getYRot();
                break;
        }
        float pitch;
        switch (facing) {
            case UP:
                pitch = 90F;
                break;
            case DOWN:
                pitch = -90F;
                break;
            default:
                pitch = 0F;
                break;
        }
        PlayerLookUtils.set(yaw, pitch);
        PlayerLookUtils.sendLookPacket();
    }

    public static void reset() {
        PlayerLookUtils.modifyYaw = false;
        PlayerLookUtils.modifyPitch = false;
        PlayerLookUtils.task = null;
        PlayerLookUtils.sendLookPacket();
    }

    public static void tick() {
        if (PlayerLookUtils.isModify()) {   // 自动重置视角
            if (PlayerLookUtils.ticks++ > 20) {
                PlayerLookUtils.ticks = 0;
                PlayerLookUtils.reset();
            }
        }
    }

    public static boolean isModify() {
        return PlayerLookUtils.modifyYaw || PlayerLookUtils.modifyPitch;
    }

    public static @Nullable Task getTask() {
        return PlayerLookUtils.task;
    }
}