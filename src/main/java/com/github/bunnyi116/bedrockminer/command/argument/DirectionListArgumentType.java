package com.github.bunnyi116.bedrockminer.command.argument;

import com.github.bunnyi116.bedrockminer.I18n;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class DirectionListArgumentType implements ArgumentType<Direction[]> {
    private static final DynamicCommandExceptionType INVALID_STRING_EXCEPTION = new DynamicCommandExceptionType(input -> Text.literal(I18n.COMMAND_EXCEPTION_INVALID_STRING.getString().replace("%input%", input.toString())));

    public static Direction getDirection(CommandContext<FabricClientCommandSource> context, String name) {
        return context.getArgument(name, Direction.class);
    }


    public Direction[] parse(StringReader reader) throws CommandSyntaxException {
        ArrayList<Direction> list = new ArrayList<Direction>();
        while (reader.canRead()) {
            if (reader.peek() == ',') {
                reader.skip();
                continue;
            }
            int cursor = reader.getCursor();
            while (reader.peek() != ',') {
                reader.skip();
            }
            String string = reader.getString().substring(cursor, reader.getCursor());
            Direction facing = null;
            for (Direction direction : Direction.values()) {
                if (string.equalsIgnoreCase(direction.getId())) {
                    facing = direction;
                }
            }
            if (facing == null) {
                reader.setCursor(cursor);
                throw INVALID_STRING_EXCEPTION.create(string);
            } else {
                list.add(facing);
            }
        }
        return list.toArray(Direction[]::new);
    }


    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());
        int cursor = reader.getCursor();
        while (reader.canRead()) {
            reader.skip();
        }
        String string = reader.getString().substring(cursor, reader.getCursor());
        for (Direction direction : Direction.values()) {
            if (direction.getId().contains(string)) {
                builder.suggest(direction.getId());
            }
        }
        return builder.buildFuture();
    }

}
