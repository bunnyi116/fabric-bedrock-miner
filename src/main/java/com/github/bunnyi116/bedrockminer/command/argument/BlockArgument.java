package com.github.bunnyi116.bedrockminer.command.argument;

import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.util.StringReaderUtils;
import com.github.bunnyi116.bedrockminer.util.block.BlockUtils;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class BlockArgument implements ArgumentType<Block[]> {
    private static final DynamicCommandExceptionType INVALID_STRING_EXCEPTION = new DynamicCommandExceptionType(input
            -> Component.literal(I18n.COMMAND_EXCEPTION_INVALID_STRING.getString().replace("#input#", input.toString())));

    private static final Collection<String> EXAMPLES = Arrays.asList("Stone", "Bedrock", "石头", "基岩");

    private @Nullable Predicate<Block> filter;

    public BlockArgument(@Nullable Predicate<Block> filter) {
        this.filter = filter;
    }

    public BlockArgument() {
        this(null);
    }

    public static Block[] getBlock(CommandContext<FabricClientCommandSource> context, String name) {
        return context.getArgument(name, Block[].class);
    }

    public Block[] parse(StringReader reader) throws CommandSyntaxException {
        String input = StringReaderUtils.readUnquotedString(reader);
        ArrayList<Block> list = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (filter != null && !filter.test(block)) {
                continue;
            }
            if (block.getName().getString().equals(input)) {
                list.add(block);
            } else if (BlockUtils.getKey(block).toString().equals(input)) {
                list.add(block);
            }
        }
        Block[] blocks = list.toArray(list.toArray(new Block[0]));
        if (blocks.length != 0) {
            return blocks;
        }
        throw INVALID_STRING_EXCEPTION.create(input);
    }


    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());
        String input = StringReaderUtils.readUnquotedString(reader);
        Map<Block, String> blocks = new HashMap<>();
        for (var block : BuiltInRegistries.BLOCK) {
            String blockId = BlockUtils.getKeyString(block);
            if (blockId.contains(input)) {
                blocks.put(block, blockId);
            }
            String blockName = block.getName().getString();
            if (blockName.contains(input)) {
                blocks.put(block, blockName);
            }
        }
        for (Map.Entry<Block, String> entry : blocks.entrySet()) {
            if (filter != null && !filter.test(entry.getKey())) {
                continue;
            }
            builder.suggest(entry.getValue());
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
