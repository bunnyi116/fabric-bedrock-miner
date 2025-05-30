package com.github.bunnyi116.bedrockminer.command.commands;

import com.github.bunnyi116.bedrockminer.Debug;
import com.github.bunnyi116.bedrockminer.command.argument.BlockPosArgumentType;
import com.github.bunnyi116.bedrockminer.util.ClientPlayerInteractionManagerUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.util.math.Direction;

import static com.github.bunnyi116.bedrockminer.Mod.world;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TestCommand {

    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(literal("test").executes(TestCommand::executes)
                .then(argument("blockPos", BlockPosArgumentType.blockPos()).executes(TestCommand::executesBlockPos)));
    }

    public static int executes(CommandContext<FabricClientCommandSource> context) {
//        var client = MinecraftClient.getInstance();
//        var world = client.world;
//        var player = client.player;
//        var hitResult = client.crosshairTarget;
//        var interactionManager = client.interactionManager;
//        var networkHandler = client.getNetworkHandler();
//        if (world == null || player == null || hitResult == null || networkHandler == null)
//            return 0;
//        // 方块选中
//        if (hitResult.getType() == HitResult.Type.BLOCK) {
//            var blockHitResult = (BlockHitResult) hitResult;
//            var blockPos = blockHitResult.getBlockPos();
//            var blockState = world.getBlockState(blockPos);
//            var block = blockState.getBlock();
//        }
        return 0;
    }

    private static int executesBlockPos(CommandContext<FabricClientCommandSource> context) {
        var targetPos = BlockPosArgumentType.getBlockPos(context, "blockPos");
        var targetState = Blocks.PISTON.getDefaultState()
                .with(PistonBlock.FACING, Direction.WEST)
                .with(PistonBlock.EXTENDED, true);
        var blockState = world.getBlockState(targetPos);
        var placeAt1 = ClientPlayerInteractionManagerUtils.canPlace(targetPos, targetState);
        Debug.alwaysWrite("Place: " + placeAt1);
        return 0;
    }
}
