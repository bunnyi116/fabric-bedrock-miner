package com.github.bunnyi116.bedrockminer.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class BlockPosArgumentType implements ArgumentType<BlockPos> {
    private static final Collection<String> EXAMPLES = Arrays.asList("0 0 0", "~ ~ ~");

    public static BlockPosArgumentType blockPos() {
        return new BlockPosArgumentType();
    }

    public static BlockPos getBlockPos(CommandContext<FabricClientCommandSource> context, String name) {
        return context.getArgument(name, BlockPos.class);
    }

    @Override
    public BlockPos parse(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        if (reader.canRead()) {
            int x = parseCoordinate(reader, 0);
            if (reader.canRead()) {
                int y = parseCoordinate(reader, 1);
                if (reader.canRead()) {
                    int z = parseCoordinate(reader, 2);
                    return new BlockPos(x, y, z);
                }
            }
        }
        reader.setCursor(i);
        throw Vec3ArgumentType.INCOMPLETE_EXCEPTION.createWithContext(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        StringReader reader = new StringReader(remaining);
        String xString = null;
        String yString = null;
        String zString = null;
        if (reader.canRead() && (reader.peek() == '~' || isAllowedInteger(reader.peek()))) {
            int cursor = reader.getCursor();
            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }
            xString = reader.getString().substring(cursor, reader.getCursor());
        }
        reader.skipWhitespace();
        if (reader.canRead() && (reader.peek() == '~' || isAllowedInteger(reader.peek()))) {
            int cursor = reader.getCursor();
            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }
            yString = reader.getString().substring(cursor, reader.getCursor());
        }
        reader.skipWhitespace();
        if (reader.canRead() && (reader.peek() == '~' || isAllowedInteger(reader.peek()))) {
            int cursor = reader.getCursor();
            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }
            zString = reader.getString().substring(cursor, reader.getCursor());
        }

        HitResult hitResult = MinecraftClient.getInstance().crosshairTarget;
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult  blockHitResult = (BlockHitResult) hitResult;
            BlockPos blockPos = blockHitResult.getBlockPos();
            if (xString == null && yString == null && zString == null) {
                builder.suggest(blockPos.getX());
                builder.suggest(String.format("%s %s", blockPos.getX(), blockPos.getY()));
                builder.suggest(String.format("%s %s %s", blockPos.getX(), blockPos.getY(), blockPos.getZ()));
            }
            if (xString != null && yString == null && zString == null) {
                builder.suggest(String.format("%s %s", xString, blockPos.getY()));
                builder.suggest(String.format("%s %s %s", xString, blockPos.getY(), blockPos.getZ()));
            }
            if (xString != null && yString != null && zString == null) {
                builder.suggest(String.format("%s %s %s", xString, yString, blockPos.getZ()));
            }
        } else {
            if (xString == null && yString == null && zString == null) {
                builder.suggest("~");
                builder.suggest("~ ~");
                builder.suggest("~ ~ ~");
            }
            if (xString != null && yString == null && zString == null) {
                builder.suggest(String.format("%s ~", xString));
                builder.suggest(String.format("%s ~ ~", xString));
            }
            if (xString != null && yString != null && zString == null) {
                builder.suggest(String.format("%s %s ~", xString, yString));
            }
        }
        return builder.buildFuture();
    }

    private int parseCoordinate(StringReader reader, int type) throws CommandSyntaxException {
        reader.skipWhitespace();
        int cursor = reader.getCursor();
        if (reader.canRead() && reader.peek() != ' ') {
            if (reader.peek() == '~') {
                reader.skip();
                // 偏移量
                int offset = 0;
                if (reader.canRead() && isAllowedInteger(reader.peek())) {
                    offset = reader.readInt();
                }
                // 玩家位置
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                if (type == 0) {
                    return (player == null ? 0 : player.getBlockX()) + offset;
                } else if (type == 1) {
                    return (player == null ? 0 : player.getBlockY()) + offset;
                } else if (type == 2) {
                    return (player == null ? 0 : player.getBlockZ()) + offset;
                }
                return 0;

            } else if (isAllowedInteger(reader.peek())) {
                return reader.readInt();
            }
        }
        reader.setCursor(cursor);
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().create(reader.getRemaining());
    }

    public static boolean isAllowedInteger(final char c) {
        return c >= '0' && c <= '9' || c == '-';
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
