package com.github.bunnyi116.bedrockminer.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {

    @Accessor
    BlockPos getDestroyBlockPos();

    @Accessor
    void setDestroyBlockPos(BlockPos destroyBlockPos);

    @Accessor
    ItemStack getDestroyingItem();

    @Accessor
    void setDestroyingItem(ItemStack destroyingItem);

    @Accessor
    float getDestroyProgress();

    @Accessor
    void setDestroyProgress(float destroyProgress);

    @Accessor
    float getDestroyTicks();

    @Accessor
    void setDestroyTicks(float destroyTicks);

    @Accessor
    int getDestroyDelay();

    @Accessor
    void setDestroyDelay(int destroyDelay);

    @Accessor("isDestroying")
    boolean isDestroying();

    @Accessor("isDestroying")
    void setDestroying(boolean destroying);
}
