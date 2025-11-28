package com.github.bunnyi116.bedrockminer.command.argument;

import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.util.StringReaderUtils;
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
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class BlockArgument implements ArgumentType<Block> {
    private static final DynamicCommandExceptionType INVALID_STRING_EXCEPTION = new DynamicCommandExceptionType(input
            -> Text.literal(I18n.COMMAND_EXCEPTION_INVALID_STRING.getString().replace("#input#", input.toString())));

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
        String input = StringReaderUtils.readUnquotedString(reader);
        Block blockResult = null;
        for (Block block : Registries.BLOCK) {
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
        StringReader reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());
        String input = StringReaderUtils.readUnquotedString(reader);
        for (Block block : Registries.BLOCK) {
            String blockName = block.getName().getString();
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
