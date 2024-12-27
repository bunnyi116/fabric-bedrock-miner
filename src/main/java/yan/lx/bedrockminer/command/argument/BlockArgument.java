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

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

public class BlockArgument implements ArgumentType<Block> {
    private static final DynamicCommandExceptionType INVALID_STRING_EXCEPTION = new DynamicCommandExceptionType(input
            -> Text.literal(LanguageText.EXCEPTION_INVALID_STRING.getString().replace("%input%", input.toString())));
    private static final Collection<String> EXAMPLES = Arrays.asList("Stone", "Bedrock", "石头", "基岩");

    private @Nullable Predicate<Identifier> filter;

    public BlockArgument(@Nullable Predicate<Identifier> filter) {
        this.filter = filter;
    }

    public BlockArgument() {
        this(null);
    }

    public static Block getBlock(CommandContext<FabricClientCommandSource> context, String name) {
        return context.getArgument(name, Block.class);
    }

    public Block parse(StringReader reader) throws CommandSyntaxException {
        var i = reader.getCursor();
        while (reader.canRead()) {
            reader.skip();
        }
        // 获取用户输入的字符串内容
        var string = reader.getString().substring(i, reader.getCursor());
        // 检查方块注册表中是否存在该名称
        var optionalBlock = Registries.BLOCK.stream().filter(block -> block.getName().getString().equals(string)).findFirst();
        if (optionalBlock.isEmpty()) {
            reader.setCursor(i);
            throw INVALID_STRING_EXCEPTION.create(string);
        }
        // 已获取到方块信息
        var block = optionalBlock.get();
        // 检查过滤器
        if (filter != null && !filter.test(BlockUtils.getIdentifier(block))) {
            reader.setCursor(i);
            throw INVALID_STRING_EXCEPTION.create(string);
        }
        return block;
    }


    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());
        // 读取标识符
        var i = reader.getCursor();
        while (reader.canRead()) {
            reader.skip();
        }
        // 获取用户输入的字符串内容
        var string = reader.getString().substring(i, reader.getCursor());
        // 检查方块注册表中是否存在该名称
        Registries.BLOCK.forEach(block -> {
            if (block.getName().getString().contains(string)) {
                if (filter != null && filter.test(BlockUtils.getIdentifier(block))) {
                    // 添加建议列表
                    builder.suggest(block.getName().getString());
                }
            }
        });
        return builder.buildFuture();
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public BlockArgument setFilter(Predicate<Identifier> filter) {
        this.filter = filter;
        return this;
    }
}
