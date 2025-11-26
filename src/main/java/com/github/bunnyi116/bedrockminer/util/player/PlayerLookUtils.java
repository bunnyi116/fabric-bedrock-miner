package com.github.bunnyi116.bedrockminer.util.player;

import com.github.bunnyi116.bedrockminer.task.Task;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.networkHandler;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;

public class PlayerLookUtils {
    private static boolean modifyYaw = false;
    private static boolean modifyPitch = false;
    private static float yaw = 0F;
    private static float pitch = 0F;
    private static int ticks = 0;
    private static @Nullable Task task = null;

    public static Direction getPlacementDirection() {
        float currentYaw = PlayerLookUtils.modifyYaw ? PlayerLookUtils.yaw : (player != null ? player.getYaw() : 0F);
        float currentPitch = PlayerLookUtils.modifyPitch ? PlayerLookUtils.pitch : (player != null ? player.getPitch() : 0F);
        return getDirectionFromYawPitch(currentYaw, currentPitch);
    }

    // 静态工具方法：根据yaw和pitch计算方向
    public static Direction getDirectionFromYawPitch(float yaw, float pitch) {
        // 标准化yaw到0-360范围
        float normalizedYaw = MathHelper.wrapDegrees(yaw);
        if (normalizedYaw < 0) {
            normalizedYaw += 360;
        }

        // 根据pitch判断上下方向
        if (pitch <= -45) {
            return Direction.UP;
        } else if (pitch >= 45) {
            return Direction.DOWN;
        }

        // 根据yaw判断水平方向
        if (normalizedYaw >= 315 || normalizedYaw < 45) {
            return Direction.SOUTH;
        } else if (normalizedYaw >= 45 && normalizedYaw < 135) {
            return Direction.WEST;
        } else if (normalizedYaw >= 135 && normalizedYaw < 225) {
            return Direction.NORTH;
        } else {
            return Direction.EAST;
        }
    }

    // 获取当前水平方向（忽略上下看）
    public static Direction getPlacementHorizontalDirection() {
        float currentYaw = PlayerLookUtils.modifyYaw ? PlayerLookUtils.yaw : (player != null ? player.getYaw() : 0F);
        return getHorizontalDirectionFromYaw(currentYaw);
    }

    // 静态工具方法：根据yaw计算水平方向
    public static Direction getHorizontalDirectionFromYaw(float yaw) {
        float normalizedYaw = MathHelper.wrapDegrees(yaw);
        if (normalizedYaw < 0) {
            normalizedYaw += 360;
        }

        if (normalizedYaw >= 315 || normalizedYaw < 45) {
            return Direction.SOUTH;
        } else if (normalizedYaw >= 45 && normalizedYaw < 135) {
            return Direction.WEST;
        } else if (normalizedYaw >= 135 && normalizedYaw < 225) {
            return Direction.NORTH;
        } else {
            return Direction.EAST;
        }
    }

    public static float onModifyLookYaw(float yaw) {
        return PlayerLookUtils.modifyYaw ? PlayerLookUtils.yaw : yaw;
    }

    public static float onModifyLookPitch(float pitch) {
        return PlayerLookUtils.modifyPitch ? PlayerLookUtils.pitch : pitch;
    }

    public static PlayerMoveC2SPacket getLookPacket(ClientPlayerEntity player, float yaw, float pitch) {
        //#if MC > 12101
        return new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), false);
        //#else
        //$$ return new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround());
        //#endif
    }

    public static void sendLookPacket(ClientPlayNetworkHandler networkHandler, PlayerMoveC2SPacket packet) {
        if (networkHandler != null) {
            networkHandler.sendPacket(packet);
        }
    }

    public static void sendLookPacket(ClientPlayNetworkHandler networkHandler, float yaw, float pitch) {
        final PlayerMoveC2SPacket packet = PlayerLookUtils.getLookPacket(player, yaw, pitch);
        PlayerLookUtils.sendLookPacket(networkHandler, packet);
    }

    private static PlayerMoveC2SPacket getLookPacket(ClientPlayerEntity player) {
        var yaw = PlayerLookUtils.modifyYaw ? PlayerLookUtils.yaw : player.getYaw();
        var pitch = PlayerLookUtils.modifyPitch ? PlayerLookUtils.pitch : player.getPitch();
        return getLookPacket(player, yaw, pitch);
    }

    public static void sendLookPacket() {
        if (networkHandler != null && player != null) {
            networkHandler.sendPacket(PlayerLookUtils.getLookPacket(player));
        }
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
        final var yaw = switch (facing) {
            case SOUTH -> 180F;
            case EAST -> 90F;
            case NORTH -> 0F;
            case WEST -> -90F;
            default -> player == null ? 0F : player.getYaw();
        };
        final var pitch = switch (facing) {
            case UP -> 90F;
            case DOWN -> -90F;
            default -> 0F;
        };
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