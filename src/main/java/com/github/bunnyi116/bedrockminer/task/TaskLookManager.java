package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.util.DirectionUtils;
import com.github.bunnyi116.bedrockminer.util.NetworkUtils;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;

public class TaskLookManager {
    public static final TaskLookManager INSTANCE = new TaskLookManager();

    private boolean modifyYaw = false;
    private boolean modifyPitch = false;
    private float yaw = 0F;
    private float pitch = 0F;
    private int ticks = 0;
    private @Nullable Task task = null;

    public float getYaw(float yaw) {
        return this.modifyYaw ? this.yaw : yaw;
    }

    public float getPitch(float pitch) {
        return this.modifyPitch ? this.pitch : pitch;
    }

    public Direction getPlacementDirection() {
        float currentYaw = getYaw(player != null ? player.getYRot() : 0F);
        float currentPitch = getPitch(player != null ? player.getXRot() : 0F);
        return DirectionUtils.orderedByNearest(currentYaw, currentPitch)[0].getOpposite();
    }

    public void sendPlayerLookPacket() {
        sendLookPacket(player.getXRot(), player.getYRot());
    }

    public void sendLookPacket(Direction facing) {
        Direction playerShouldBeFacing = facing.getOpposite();
        float yaw = DirectionUtils.getRequiredYaw(playerShouldBeFacing);
        float pitch = DirectionUtils.getRequiredPitch(playerShouldBeFacing);
        sendLookPacket(yaw, pitch);
    }

    public static void sendLookPacket(float lookYaw, float lookPitch) {
        NetworkUtils.sendPacket(getLookPacket(lookYaw, lookPitch));
    }

    public static ServerboundMovePlayerPacket getLookPacket(float yaw, float pitch) {
        //#if MC > 12101
        return new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), false);
        //#else
        //$$ return new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround());
        //#endif
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
        Direction playerShouldBeFacing = facing.getOpposite();
        float yaw = DirectionUtils.getRequiredYaw(playerShouldBeFacing);
        float pitch = DirectionUtils.getRequiredPitch(playerShouldBeFacing);
        this.set(yaw, pitch);
        this.sendPlayerLookPacket();
    }

    public void reset() {
        this.modifyYaw = false;
        this.modifyPitch = false;
        this.task = null;
        this.sendPlayerLookPacket();
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