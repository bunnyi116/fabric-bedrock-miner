package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.mixin_extension.MultiPlayerGameModeExtension;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Inject(at = @At(value = "HEAD"), method = "startDestroyBlock")
    private void attackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (TaskManager.getInstance().isRunning()) {
            BlockState blockState = level.getBlockState(pos);
            Block block = blockState.getBlock();
            if (TaskManager.getInstance().isBedrockMinerFeatureEnable()) {
                TaskManager.getInstance().addBlockTask(level, pos, block);
            }
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "useItemOn")
    private void interactBlock(LocalPlayer player, InteractionHand hand, BlockHitResult blockHit, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos blockPos = blockHit.getBlockPos();
        BlockState blockState = level.getBlockState(blockPos);
        Block block = blockState.getBlock();
        if (interactBlockCooldown > 0) {
            interactBlockCooldown--;
            return;
        }
        interactBlockCooldown = 1;
        if (TaskManager.getInstance().isBedrockMinerFeatureEnable() && BedrockMiner.player.getMainHandItem().isEmpty() && !Config.getInstance().disableEmptyHandSwitchToggle) {
            TaskManager.getInstance().switchToggle(block);
        }
    }

}
