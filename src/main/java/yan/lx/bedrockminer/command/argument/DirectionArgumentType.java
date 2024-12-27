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

import java.util.concurrent.CompletableFuture;

public class DirectionArgumentType implements ArgumentType<Direction> {
    private static final DynamicCommandExceptionType INVALID_STRING_EXCEPTION = new DynamicCommandExceptionType(input -> Text.literal(LanguageText.EXCEPTION_INVALID_STRING.getString().replace("%input%", input.toString())));

    public static Direction getDirection(CommandContext<FabricClientCommandSource> context, String name) {
        return context.getArgument(name, Direction.class);
    }

    public Direction parse(StringReader reader) throws CommandSyntaxException {
        var i = reader.getCursor();
        while (reader.canRead()) {
            reader.skip();
        }
        var string = reader.getString().substring(i, reader.getCursor());
        for (Direction direction : Direction.values()) {
            if (string.equalsIgnoreCase(direction.getName())) {
                return direction;
            }
        }
        reader.setCursor(i);
        throw INVALID_STRING_EXCEPTION.create(string);
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
