package com.github.bunnyi116.bedrockminer.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ClientPlayerInteractionManager.class, priority = 999)
public interface ClientPlayerInteractionManagerAccessor {
    @Accessor
    @NotNull
    MinecraftClient getClient();

    @Accessor
    ClientPlayNetworkHandler getNetworkHandler();

    @Accessor
    BlockPos getCurrentBreakingPos();

    @Accessor
    void setCurrentBreakingPos(BlockPos currentBreakingPos);

    @Accessor
    ItemStack getSelectedStack();

    @Accessor
    void setSelectedStack(ItemStack selectedStack);

    @Accessor
    float getCurrentBreakingProgress();

    @Accessor
    void setCurrentBreakingProgress(float currentBreakingProgress);

    @Accessor
    float getBlockBreakingSoundCooldown();

    @Accessor
    void setBlockBreakingSoundCooldown(float blockBreakingSoundCooldown);

    @Accessor
    int getBlockBreakingCooldown();

    @Accessor
    void setBlockBreakingCooldown(int blockBreakingCooldown);

    @Accessor
    boolean isBreakingBlock();

    @Accessor
    void setBreakingBlock(boolean breakingBlock);

    @Accessor
    GameMode getGameMode();

    @Accessor
    int getLastSelectedSlot();

    @Accessor
    void setLastSelectedSlot(int lastSelectedSlot);

    @Invoker("syncSelectedSlot")
    void invokeSyncSelectedSlot();

    @Invoker("attackBlock")
    boolean interactAttackBlock(BlockPos pos, Direction direction);

    @Invoker("breakBlock")
    boolean interactBreakBlock(BlockPos pos);

    @Invoker("isCurrentlyBreaking")
    boolean interactIsCurrentlyBreaking(BlockPos pos);

    @Invoker("getBlockBreakingProgress")
    int interactGetBlockBreakingProgress();

    @Invoker("interactBlockInternal")
    ActionResult interactInteractBlockInternal(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult);
}
