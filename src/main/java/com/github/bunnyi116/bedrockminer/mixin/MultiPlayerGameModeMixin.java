package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.Debug;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.mixin_extension.MultiPlayerGameModeExtension;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.github.bunnyi116.bedrockminer.util.BlockUtils;
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

    @Inject(at = @At(value = "HEAD"), method = "tick")
    private void tick(CallbackInfo ci) {
        if (interactBlockCooldown > 0) {
            interactBlockCooldown--;
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "startDestroyBlock")
    private void attackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BlockState blockState = level.getBlockState(pos);
        Block block = blockState.getBlock();
        Debug.write("attackBlock called at {} block={} isWorking={}", pos, BlockUtils.getKeyString(block), TaskManager.isWorking());
        if (TaskManager.isWorking()) {
            if (TaskManager.getInstance().isBedrockMinerFeatureEnable()) {
                TaskManager.getInstance().addBlockTask(level, pos, block);
            }
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "useItemOn")
    private void interactBlock(LocalPlayer player, InteractionHand hand, BlockHitResult blockHit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player == null || level == null) {
            Debug.write("interactBlock skipped: player or level is null");
            return;
        }
        BlockPos blockPos = blockHit.getBlockPos();
        BlockState blockState = level.getBlockState(blockPos);
        Block block = blockState.getBlock();
        Debug.write("interactBlock called at {} block={} mainHandEmpty={} featureEnable={} disableToggle={}",
                blockPos, BlockUtils.getKeyString(block), player.getMainHandItem().isEmpty(),
                TaskManager.getInstance().isBedrockMinerFeatureEnable(), Config.getInstance().disableEmptyHandSwitchToggle);
        if (hand != InteractionHand.MAIN_HAND) {
            Debug.write("interactBlock skipped: not main hand");
            return;
        }
        if (interactBlockCooldown > 0) {
            Debug.write("interactBlock cooldown active: {}", interactBlockCooldown);
            return;
        }
        interactBlockCooldown = 20;
        if (TaskManager.getInstance().isBedrockMinerFeatureEnable() && player.getMainHandItem().isEmpty() && !Config.getInstance().disableEmptyHandSwitchToggle) {
            Debug.write("interactBlock toggling for block {}", BlockUtils.getKeyString(block));
            TaskManager.getInstance().switchToggle(block);
        }
    }

}
