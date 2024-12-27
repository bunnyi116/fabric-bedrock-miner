package yan.lx.bedrockminer;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import yan.lx.bedrockminer.command.argument.BlockPosArgumentType;
import yan.lx.bedrockminer.utils.BlockPlacerUtils;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class Test {

    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(literal("test").executes(Test::executes)
                .then(argument("blockPos", BlockPosArgumentType.blockPos()).executes(Test::executesBlockPos)));
    }

    public static int executes(CommandContext<FabricClientCommandSource> context) {
        var client = MinecraftClient.getInstance();
        var world = client.world;
        var player = client.player;
        var hitResult = client.crosshairTarget;
        var interactionManager = client.interactionManager;
        var networkHandler = client.getNetworkHandler();
        if (world == null || player == null || hitResult == null || networkHandler == null)
            return 0;
        // 方块选中
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            var blockHitResult = (BlockHitResult) hitResult;
            var blockPos = blockHitResult.getBlockPos();
            var blockState = world.getBlockState(blockPos);
            var block = blockState.getBlock();
        }
        return 0;
    }

    private static int executesBlockPos(CommandContext<FabricClientCommandSource> context) {
        var blockPos = BlockPosArgumentType.getBlockPos(context, "blockPos");
        BlockPlacerUtils.placement(blockPos.up(), Direction.NORTH);
        return 0;
    }
}
