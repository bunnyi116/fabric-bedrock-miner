package com.github.bunnyi116.bedrockminer;

import com.github.bunnyi116.bedrockminer.command.argument.BlockPosArgumentType;
import com.github.bunnyi116.bedrockminer.util.BlockUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class Test {

    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(literal("test").executes(Test::executes)
                .then(argument("blockPos", BlockPosArgumentType.blockPos()).executes(Test::executesBlockPos)));
    }

    public static int executes(CommandContext<FabricClientCommandSource> context) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.BLOCK) return 0;
        if (level == null || player == null) return 0;
        BlockHitResult blockHitResult = (BlockHitResult) minecraft.hitResult;
        if (player.getMainHandItem().getItem() instanceof BlockItem blockItem) {
            BlockPos blockPos = blockHitResult.getBlockPos().above();
            BlockState blockState = blockItem.getBlock().defaultBlockState();
            Debug.alwaysWrite(BlockUtils.canPlace(blockPos, blockState));
        }
        return 1;
    }

    private static int executesBlockPos(CommandContext<FabricClientCommandSource> context) {
        return 1;
    }
}
