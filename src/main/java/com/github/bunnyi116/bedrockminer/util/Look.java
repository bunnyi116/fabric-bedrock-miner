package com.github.bunnyi116.bedrockminer.util;

import lombok.Data;
import net.minecraft.core.Direction;

@Data
@SuppressWarnings("ClassCanBeRecord")
public class Look {
    public final float yaw;
    public final float pitch;

    public Look(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Look(Direction lookDirection) {
        this(DirectionUtils.getRequiredYaw(lookDirection), DirectionUtils.getRequiredPitch(lookDirection));
    }
}
