package com.github.bunnyi116.bedrockminer.util;

import lombok.Data;
import net.minecraft.core.Direction;

@Data
@SuppressWarnings("ClassCanBeRecord")
public class PlayerLook {
    public final float yaw;
    public final float pitch;

    public PlayerLook(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public PlayerLook(Direction lookDirection) {
        this(DirectionUtils.getRequiredYaw(lookDirection), DirectionUtils.getRequiredPitch(lookDirection));
    }
}
