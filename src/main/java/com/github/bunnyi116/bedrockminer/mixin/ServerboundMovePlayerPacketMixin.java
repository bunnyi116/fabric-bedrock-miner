package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.util.PlayerLookUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ServerboundMovePlayerPacket.class, priority = 1010)
public class ServerboundMovePlayerPacketMixin {
    //#if MC > 12101
    @ModifyVariable(method = "<init>(DDDFFZZZZ)V", at = @At("HEAD"), argsOnly = true, name = "yRot")
    //#else
    //$$ @ModifyVariable(method = "<init>(DDDFFZZZ)V", at = @At("HEAD"), argsOnly = true, name = "yRot")
    //#endif
    private static float modifyLookYaw(float yaw) {
        return PlayerLookUtils.getYaw(yaw);
    }


    //#if MC > 12101
    @ModifyVariable(method = "<init>(DDDFFZZZZ)V", at = @At("HEAD"), argsOnly = true, name = "xRot")
    //#else
    //$$ @ModifyVariable(method = "<init>(DDDFFZZZ)V", at = @At("HEAD"), argsOnly = true, name = "xRot")
    //#endif
    private static float modifyLookPitch(float pitch) {
        return PlayerLookUtils.getPitch(pitch);
    }
}
