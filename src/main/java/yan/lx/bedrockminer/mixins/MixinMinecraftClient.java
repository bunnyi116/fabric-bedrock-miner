package yan.lx.bedrockminer.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import yan.lx.bedrockminer.task.TaskManager;

@Mixin(value = MinecraftClient.class, priority = 999)
public class MixinMinecraftClient {
    @Shadow
    @Nullable
    public ClientWorld world;

    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    @Shadow
    @Nullable
    public HitResult crosshairTarget;

    @Inject(method = "doItemUse", at = @At(value = "HEAD"))
    private void doItemUse(CallbackInfo ci) {
        if (crosshairTarget == null || world == null || player == null) {
            return;
        }
        if (crosshairTarget.getType() != HitResult.Type.BLOCK || !player.getMainHandStack().isEmpty()) {
            return;
        }
        var blockHitResult = (BlockHitResult) crosshairTarget;
        var blockPos = blockHitResult.getBlockPos();
        var blockState = world.getBlockState(blockPos);
        var block = blockState.getBlock();
        TaskManager.switchOnOff(block);
    }

    @Inject(method = "handleBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void handleBlockBreaking(boolean bl, CallbackInfo ci, BlockHitResult blockHitResult, BlockPos blockPos, Direction direction) {
        if (world == null) {
            return;
        }
        var blockState = world.getBlockState(blockPos);
        var block = blockState.getBlock();
        TaskManager.addTask(block, blockPos, world);
    }
}
