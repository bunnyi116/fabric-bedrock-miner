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

public class PlayerLookManager {
    public static PlayerLookManager INSTANCE = new PlayerLookManager();

    private boolean modifyYaw = false;
    private boolean modifyPitch = false;
    private float yaw = 0F;
    private float pitch = 0F;
    private int ticks = 0;
    private @Nullable Task task = null;


    public Direction getPlacementDirection() {
        float currentYaw = this.modifyYaw ? this.yaw : (player != null ? player.getYaw() : 0F);
        float currentPitch = this.modifyPitch ? this.pitch : (player != null ? player.getPitch() : 0F);
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
    public Direction getPlacementHorizontalDirection() {
        float currentYaw = this.modifyYaw ? this.yaw : (player != null ? player.getYaw() : 0F);
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

    public float onModifyLookYaw(float yaw) {
        return this.modifyYaw ? this.yaw : yaw;
    }

    public float onModifyLookPitch(float pitch) {
        return this.modifyPitch ? this.pitch : pitch;
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
        final PlayerMoveC2SPacket packet = PlayerLookManager.getLookPacket(player, yaw, pitch);
        PlayerLookManager.sendLookPacket(networkHandler, packet);
    }

    private PlayerMoveC2SPacket getLookPacket(ClientPlayerEntity player) {
        var yaw = this.modifyYaw ? this.yaw : player.getYaw();
        var pitch = this.modifyPitch ? this.pitch : player.getPitch();
        return getLookPacket(player, yaw, pitch);
    }

    public void sendLookPacket() {
        if (networkHandler != null && player != null) {
            networkHandler.sendPacket(this.getLookPacket(player));
        }
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        this.modifyYaw = true;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
        this.modifyPitch = true;
    }

    public void set(float yaw, float pitch) {
        this.setYaw(yaw);
        this.setPitch(pitch);
    }

    public void set(Direction facing, Task task) {
        this.task = task;
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
        this.set(yaw, pitch);
        this.sendLookPacket();
    }

    public void reset() {
        this.modifyYaw = false;
        this.modifyPitch = false;
        this.task = null;
        this.sendLookPacket();
    }

    public void tick() {
        if (this.isModify()) {   // 自动重置视角
            if (this.ticks++ > 20) {
                this.ticks = 0;
                this.reset();
            }
        }
    }

    public boolean isModify() {
        return this.modifyYaw || this.modifyPitch;
    }

    public @Nullable Task getTask() {
        return this.task;
    }
}