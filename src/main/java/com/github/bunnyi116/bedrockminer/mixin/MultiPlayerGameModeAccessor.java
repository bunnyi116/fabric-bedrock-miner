package com.github.bunnyi116.bedrockminer.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {

    @Accessor
    BlockPos getDestroyBlockPos();

    @Accessor
    void setDestroyBlockPos(BlockPos destroyBlockPos);

    @Accessor
    float getDestroyProgress();

    @Accessor
    void setDestroyProgress(float destroyProgress);

    @Accessor("isDestroying")
    boolean isDestroying();

    @Accessor("isDestroying")
    void setDestroying(boolean destroying);
}
