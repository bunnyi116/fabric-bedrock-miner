package yan.lx.bedrockminer.task;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import static yan.lx.bedrockminer.BedrockMiner.networkHandler;
import static yan.lx.bedrockminer.BedrockMiner.player;

public class TaskPlayerLookManager {
    private static boolean modifyYaw = false;
    private static boolean modifyPitch = false;
    private static float yaw = 0F;
    private static float pitch = 0F;
    private static int ticks = 0;
    private static @Nullable TaskHandler taskHandler = null;

    public static float onModifyLookYaw(float yaw) {
        return modifyYaw ? TaskPlayerLookManager.yaw : yaw;
    }

    public static float onModifyLookPitch(float pitch) {
        return modifyPitch ? TaskPlayerLookManager.pitch : pitch;
    }

    private static PlayerMoveC2SPacket getLookAndOnGroundPacket(ClientPlayerEntity player) {
        var yaw = modifyYaw ? TaskPlayerLookManager.yaw : player.getYaw();
        var pitch = modifyPitch ? TaskPlayerLookManager.pitch : player.getPitch();
        return new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround());
    }

    public static void set(float yaw, float pitch) {
        TaskPlayerLookManager.modifyYaw = true;
        TaskPlayerLookManager.yaw = yaw;
        TaskPlayerLookManager.modifyPitch = true;
        TaskPlayerLookManager.pitch = pitch;
    }

    public static void set(Direction facing, TaskHandler handler) {
        taskHandler = handler;
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
        if (networkHandler != null && player != null) {
            networkHandler.sendPacket(getLookAndOnGroundPacket(player));
        }
    }

    public static void reset() {
        modifyYaw = false;
        yaw = 0F;
        modifyPitch = false;
        pitch = 0F;
        taskHandler = null;
        // 发送一个还原视角的数据包
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler != null && player != null) {
            networkHandler.sendPacket(getLookAndOnGroundPacket(player));
        }
    }


    public static void onTick() {
        // 自动重置视角
        if (isModify()) {
            if (ticks++ > 20) {
                ticks = 0;
                reset();
            }
        }
    }

    public static boolean isModify() {
        return modifyYaw || modifyPitch;
    }

    public static @Nullable TaskHandler getTaskHandler() {
        return taskHandler;
    }
}
