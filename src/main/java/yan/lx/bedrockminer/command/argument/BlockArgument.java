package yan.lx.bedrockminer.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import yan.lx.bedrockminer.LanguageText;
import yan.lx.bedrockminer.utils.BlockUtils;
import yan.lx.bedrockminer.utils.StringReaderUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class BlockArgument implements ArgumentType<Block> {
    private static final DynamicCommandExceptionType INVALID_STRING_EXCEPTION = new DynamicCommandExceptionType(input
            -> Text.literal(LanguageText.COMMAND_EXCEPTION_INVALID_STRING.getString().replace("#input#", input.toString())));

    private static final Collection<String> EXAMPLES = Arrays.asList("Stone", "Bedrock", "石头", "基岩");

    private @Nullable Predicate<Block> filter;

    public BlockArgument(@Nullable Predicate<Block> filter) {
        this.filter = filter;
    }

    public BlockArgument() {
        this(null);
    }

    public static Block getBlock(CommandContext<FabricClientCommandSource> context, String name) {
        return context.getArgument(name, Block.class);
    }

    public Block parse(StringReader reader) throws CommandSyntaxException {
        var input = StringReaderUtils.readUnquotedString(reader);
        var blockResult = (Block) null;
        for (var block : Registries.BLOCK) {
            if (block.getName().getString().equals(input)) {
                blockResult = block;
                break;
            }
        }
        if (blockResult != null && filter != null && filter.test(blockResult)) {
            blockResult = null;
        }
        if (blockResult == null) {
            throw INVALID_STRING_EXCEPTION.create(input);
        }
        return blockResult;
    }


    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        var reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());
        var input = StringReaderUtils.readUnquotedString(reader);
        for (var block : Registries.BLOCK) {
            var blockName = block.getName().getString();
            if (blockName.contains(input)) {
                if (filter != null && filter.test(block)) {
                    continue;
                }
                builder.suggest(blockName);
            }
        }
        return builder.buildFuture();
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public BlockArgument setFilter(Predicate<Block> filter) {
        this.filter = filter;
        return this;
    }
}
