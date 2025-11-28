package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.util.player.PlayerLookUtils;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = PlayerMoveC2SPacket.class, priority = 999)
public class MixinPlayerMoveC2SPacket {
    //#if MC > 12101
    @ModifyVariable(method = "<init>(DDDFFZZZZ)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    //#else
    //$$ @ModifyVariable(method = "<register>(DDDFFZZZ)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    //#endif
    private static float modifyLookYaw(float yaw) {
        return  PlayerLookUtils.onModifyLookYaw(yaw);
    }


    //#if MC > 12101
    @ModifyVariable(method = "<init>(DDDFFZZZZ)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    //#else
    //$$ @ModifyVariable(method = "<register>(DDDFFZZZ)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    //#endif
    private static float modifyLookPitch(float pitch) {
        return  PlayerLookUtils.onModifyLookPitch(pitch);
    }
}
