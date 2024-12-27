package yan.lx.bedrockminer.command.argument;

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
import yan.lx.bedrockminer.LanguageText;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class DirectionListArgumentType implements ArgumentType<Direction[]> {
    private static final DynamicCommandExceptionType INVALID_STRING_EXCEPTION = new DynamicCommandExceptionType(input -> Text.literal(LanguageText.EXCEPTION_INVALID_STRING.getString().replace("%input%", input.toString())));

    public static Direction getDirection(CommandContext<FabricClientCommandSource> context, String name) {
        return context.getArgument(name, Direction.class);
    }


    public Direction[] parse(StringReader reader) throws CommandSyntaxException {
        var list = new ArrayList<Direction>();
        while (reader.canRead()) {
            if (reader.peek() == ',') {
                reader.skip();
                continue;
            }
            var i = reader.getCursor();
            while (reader.peek() != ',') {
                reader.skip();
            }
            var string = reader.getString().substring(i, reader.getCursor());
            Direction facing = null;
            for (Direction direction : Direction.values()) {
                if (string.equalsIgnoreCase(direction.getName())) {
                    facing = direction;
                }
            }
            if (facing == null) {
                reader.setCursor(i);
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
        var i = reader.getCursor();
        while (reader.canRead()) {
            reader.skip();
        }
        var string = reader.getString().substring(i, reader.getCursor());
        for (Direction direction : Direction.values()) {
            if (direction.getName().contains(string)) {
                builder.suggest(direction.getName());
            }
        }
        return builder.buildFuture();
    }

}
