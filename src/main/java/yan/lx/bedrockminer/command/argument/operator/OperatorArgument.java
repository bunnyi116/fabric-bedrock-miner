package yan.lx.bedrockminer.command.argument.operator;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Block;
import net.minecraft.text.Text;
import yan.lx.bedrockminer.LanguageText;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class OperatorArgument implements ArgumentType<Operator> {
    private static final DynamicCommandExceptionType INVALID_STRING_EXCEPTION = new DynamicCommandExceptionType(input -> Text.literal(LanguageText.COMMAND_EXCEPTION_INVALID_STRING.getString().replace("%input%", input.toString())));

    private static final Collection<String> EXAMPLES = Arrays.asList(">", ">=", "==", "<=", "<");

    public static Operator getOperator(CommandContext<FabricClientCommandSource> context, String name) {
        return context.getArgument(name, Operator.class);
    }

    @Override
    public Operator parse(StringReader reader) throws CommandSyntaxException {
        var input = reader.readUnquotedString();
        var operatorType = Operator.fromString(input);
        if (operatorType == null) {
            throw INVALID_STRING_EXCEPTION.create(input);
        }
        return operatorType;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        var reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());
        var input = readUnquotedString(reader);
        for (var operatorString : Operator.getStringValues()) {
            if (operatorString.contains(input)) {
                builder.suggest(operatorString);
            }
        }
        return builder.buildFuture();
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public String readUnquotedString(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead()) {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }
}
