package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.task.block.TaskSchemeBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.networkHandler;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;

public class TaskPlayerLookManager {
    public static final TaskPlayerLookManager INSTANCE = new TaskPlayerLookManager();

    private boolean modifyYaw = false;
    private boolean modifyPitch = false;
    private float yaw = 0F;
    private float pitch = 0F;
    private int ticks = 0;
    private @Nullable Task task = null;
    private @Nullable TaskBlock taskBlock = null;

    public float onModifyLookYaw(float yaw) {
        return this.modifyYaw ? this.yaw : yaw;
    }

    public float onModifyLookPitch(float pitch) {
        return this.modifyPitch ? this.pitch : pitch;
    }

    private PlayerMoveC2SPacket getLookAndOnGroundPacket() {
        var yaw = this.modifyYaw ? this.yaw : player.getYaw();
        var pitch = this.modifyPitch ? this.pitch : player.getPitch();
        return new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), false);
    }

    public void sendLookAndOnGroundPacket() {
        networkHandler.sendPacket(getLookAndOnGroundPacket());
    }


    private void setYaw(float yaw) {
        this.modifyYaw = true;
        this.yaw = yaw;
    }

    private void setYawAndSendPacket(float yaw) {
        this.setYaw(yaw);
        this.sendLookAndOnGroundPacket();
    }

    private void setPitch(float pitch) {
        this.modifyPitch = true;
        this.pitch = pitch;
    }

    private void setPitchAndSendPacket(float pitch) {
        this.setPitch(pitch);
        this.sendLookAndOnGroundPacket();
    }

    public void setYawPitch(float yaw, float pitch) {
        this.setYaw(yaw);
        this.setPitch(pitch);
    }

    private void setYawPitchAndSendPacket(float yaw, float pitch) {
        this.setYaw(yaw);
        this.setPitch(pitch);
        this.sendLookAndOnGroundPacket();
    }

    public void setFacing(Direction facing, @Nullable Task task, @Nullable TaskBlock taskBlock) {
        this.task = task;
        this.taskBlock = taskBlock;
        var yaw = switch (facing) {
            case SOUTH -> 180F;
            case EAST -> 90F;
            case NORTH -> 0F;
            case WEST -> -90F;
            default -> player == null ? 0F : player.getYaw();
        };
        var pitch = switch (facing) {
            case UP -> 90F;
            case DOWN -> -90F;
            default -> 0F;
        };
        this.setYawPitchAndSendPacket(yaw, pitch);
    }


    public void reset(boolean resetTask, boolean resetTaskBlock) {
        this.modifyYaw = false;
        this.modifyPitch = false;
        if (resetTask) {
            this.task = null;
        }
        if (resetTaskBlock) {
            this.taskBlock = null;
        }
    }

    public void reset() {
        this.reset(true, true);
    }

    public void resetAndSendPacket(boolean resetTask, boolean resetTaskBlock) {
        this.reset(resetTask, resetTaskBlock);
        this.sendLookAndOnGroundPacket();
    }

    public void resetAndSendPacket() {
        this.resetAndSendPacket(true, true);
    }

    public void tickAutoReset() {
        if (isModify()) {
            if (this.ticks++ > 20) {
                this.ticks = 0;
                reset(true, true);
            }
        } else {
            this.ticks = 0;
        }
    }

    public boolean isModify() {
        return this.modifyYaw || this.modifyPitch;
    }

    public boolean isModify(Task task) {
        return this.isModify() && this.task == task;
    }

    public boolean isModify(TaskBlock taskBlock) {
        return this.isModify() && this.taskBlock == taskBlock;
    }

    public boolean isModify(Task task, TaskBlock taskBlock) {
        return this.isModify() && this.task == task && this.taskBlock == taskBlock;
    }

    public boolean isOf(Task task) {
        return this.task != null && this.task == task;
    }

    public boolean isOf(TaskBlock taskBlock) {
        return this.taskBlock != null && this.taskBlock == taskBlock;
    }

    public boolean isOf(Task task, TaskBlock taskBlock) {
        if (this.task != null && this.task.equals(task)) {
            return true;
        }
        return this.taskBlock != null && this.taskBlock.equals(taskBlock);
    }
}
