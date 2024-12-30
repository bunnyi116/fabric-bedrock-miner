package yan.lx.bedrockminer;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yan.lx.bedrockminer.command.argument.BlockPosArgumentType;
import yan.lx.bedrockminer.utils.BlockPlacerUtils;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static yan.lx.bedrockminer.BedrockMiner.*;

public class Test {

    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(literal("test").executes(Test::executes)
                .then(argument("blockPos", BlockPosArgumentType.blockPos()).executes(Test::executesBlockPos)));
    }

    public static int executes(CommandContext<FabricClientCommandSource> context) {
        var targetPos = new BlockPos(4, -58, -17);
        var targetFacing = Direction.SOUTH;
        var hitBlockPos = targetPos.offset(targetFacing.getOpposite());

        // 模拟方块选中的中心位置
        var offsetX = targetFacing.getOffsetX() * 0.5;
        var offsetY = targetFacing.getOffsetY() * 0.5;
        var offsetZ = targetFacing.getOffsetZ() * 0.5;
        var hitCenterPos = hitBlockPos.toCenterPos();
        var facePos = hitCenterPos.add(offsetX, offsetY, offsetZ);
        var hPos = new BlockHitResult(facePos, targetFacing, targetPos, false);
        var yaw = switch (targetFacing) {
            case SOUTH -> 180F;
            case EAST -> 90F;
            case NORTH -> 0F;
            case WEST -> -90F;
            default -> player.getYaw();
        };
        var pitch = switch (targetFacing) {
            case UP -> 90F;
            case DOWN -> -90F;
            default -> 0F;
        };
        networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), false));
        networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), false));
        interactionManager.interactBlock(player, Hand.MAIN_HAND, hPos);

        // 十字准星目标
        if (crosshairTarget != null) {
            var hitResult = crosshairTarget;
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                var blockHitResult = (BlockHitResult) hitResult;
                var pos = blockHitResult.getPos();      // 玩家选中方块的面坐标
                var blockPos = blockHitResult.getBlockPos();    // 目标方块坐标
                var blockState = world.getBlockState(blockPos);
                var block = blockState.getBlock();
                var blockName = block.getName().getString();
                var side = blockHitResult.getSide();    // 玩家选中方块的那个侧面
                // Debug.alwaysWrite("{}, {} -> {}, {}, {}", blockName, blockPos.toShortString(), pos, facePos, side);

            }
        }
        return 0;
    }

    private static int executesBlockPos(CommandContext<FabricClientCommandSource> context) {
        var blockPos = BlockPosArgumentType.getBlockPos(context, "blockPos");
        BlockPlacerUtils.placement(blockPos.up(), Direction.NORTH);
        return 0;
    }



}
