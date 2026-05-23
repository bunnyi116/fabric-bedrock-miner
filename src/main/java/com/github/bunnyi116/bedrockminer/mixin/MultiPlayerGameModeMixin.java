package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.mixin_extension.MultiPlayerGameModeExtension;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.github.bunnyi116.bedrockminer.util.InteractionUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.level;

@Mixin(value = MultiPlayerGameMode.class, priority = 1010)
public abstract class MultiPlayerGameModeMixin implements MultiPlayerGameModeExtension {

    @Shadow
    @Final
    public abstract void ensureHasSentCarriedItem();

    @Override
    public void bedrockminer$ensureHasSentCarriedItem() {
        this.ensureHasSentCarriedItem();
    }

    @Unique
    private int interactBlockCooldown = 0;

    @Inject(at = @At(value = "HEAD"), method = "startDestroyBlock", cancellable = true)
    private void attackBlock(BlockPos blockPos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (TaskManager.isWorking()) {
            BlockState blockState = level.getBlockState(blockPos);
            Block block = blockState.getBlock();
            if (TaskManager.getInstance().isBedrockMinerFeatureEnable()) {
                TaskManager.getInstance().addBlockTask(level, blockPos, block);
            }
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "useItemOn", cancellable = true)
    private void interactBlock(LocalPlayer localPlayer, InteractionHand interactionHand, BlockHitResult blockHitResult, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos blockPos = blockHitResult.getBlockPos();
        BlockState blockState = level.getBlockState(blockPos);
        Block block = blockState.getBlock();
        if (interactBlockCooldown > 0) {
            interactBlockCooldown--;
            return;
        }
        interactBlockCooldown = 1;
        if (TaskManager.getInstance().isBedrockMinerFeatureEnable() && player.getMainHandItem().isEmpty() && !Config.getInstance().disableEmptyHandSwitchToggle) {
            TaskManager.getInstance().switchToggle(block);
        }
    }

}
