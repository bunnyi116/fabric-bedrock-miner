package com.github.bunnyi116.bedrockminer;

import com.github.bunnyi116.bedrockminer.command.argument.BlockPosArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class Test {

    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(literal("test").executes(Test::executes)
                .then(argument("blockPos", BlockPosArgumentType.blockPos()).executes(Test::executesBlockPos)));
    }

    public static int executes(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        HitResult hitResult = client.crosshairTarget;
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (world == null || player == null || hitResult == null || networkHandler == null)
            return 0;
        // 方块选中
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult  blockHitResult = (BlockHitResult) hitResult;
            BlockPos blockPos = blockHitResult.getBlockPos();
            BlockState blockState = world.getBlockState(blockPos);
            Block block = blockState.getBlock();
        }
        return 0;
    }

    private static int executesBlockPos(CommandContext<FabricClientCommandSource> context) {
        BlockPos blockPos = BlockPosArgumentType.getBlockPos(context, "blockPos");
        return 0;
    }
}
