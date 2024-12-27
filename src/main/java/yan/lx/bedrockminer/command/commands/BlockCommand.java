package yan.lx.bedrockminer.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Block;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import yan.lx.bedrockminer.LanguageText;
import yan.lx.bedrockminer.command.CommandBase;
import yan.lx.bedrockminer.command.argument.BlockArgument;
import yan.lx.bedrockminer.config.Config;
import yan.lx.bedrockminer.utils.BlockUtils;
import yan.lx.bedrockminer.utils.MessageUtils;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class BlockCommand extends CommandBase {
    @Override
    public String getName() {
        return "block";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder, CommandRegistryAccess registryAccess) {
        builder.then(literal("whitelist")
                        .then(literal("add")
                                .then(argument("block", new BlockArgument(this::filterWhitelist))
                                        .executes(context -> add(context, true))
                                )
                        )
                        .then(literal("remove")
                                .then(argument("block", new BlockArgument(this::showWhitelist))
                                        .executes(context -> remove(context, true))
                                )
                        ))
                .then(literal("blacklist")
                        .then(literal("add")
                                .then(argument("block", new BlockArgument(this::filterBlacklist))
                                        .executes(context -> add(context, false))
                                )
                        )
                        .then(literal("remove")
                                .then(argument("block", new BlockArgument(this::showBlacklist))
                                        .executes(context -> remove(context, false))
                                )
                        )
                );
    }

    private int add(CommandContext<FabricClientCommandSource> context, boolean whitelist) {
        var block = BlockArgument.getBlock(context, "block");
        var config = Config.INSTANCE;
        var id = BlockUtils.getId(block);
        if (whitelist && !config.blockWhitelist.contains(id)) {
            config.blockWhitelist.add(id);
            Config.save();
            sendChat(LanguageText.COMMAND_BLOCK_WHITELIST_ADD, block);
        } else if (!config.blockBlacklist.contains(id)) {
            config.blockBlacklist.add(id);
            Config.save();
            sendChat(LanguageText.COMMAND_BLOCK_BLACKLIST_ADD, block);
        }
        return 0;
    }

    private int remove(CommandContext<FabricClientCommandSource> context, boolean whitelist) {
        var block = BlockArgument.getBlock(context, "block");
        var config = Config.INSTANCE;
        var id = BlockUtils.getId(block);
        if (whitelist && config.blockWhitelist.contains(id)) {
            config.blockWhitelist.remove(id);
            Config.save();
            sendChat(LanguageText.COMMAND_BLOCK_WHITELIST_REMOVE, block);
        } else if (config.blockBlacklist.contains(id)) {
            config.blockBlacklist.remove(id);
            Config.save();
            sendChat(LanguageText.COMMAND_BLOCK_BLACKLIST_REMOVE, block);
        }
        return 0;
    }

    private boolean isFilterBlock(Identifier blockId) {
        var block = Registries.BLOCK.get(blockId);
        // 过滤空气和可替换方块
        return block.getDefaultState().isAir() || block.getDefaultState().isReplaceable();
    }

    private Boolean showWhitelist(Identifier blockId) {
        var config = Config.INSTANCE;
        for (var whitelist : config.blockWhitelist) {
            if (blockId.toString().equals(whitelist)) {
                return true;
            }
        }
        return false;
    }

    private Boolean filterWhitelist(Identifier blockId) {
        if (isFilterBlock(blockId)) {
            return false;
        }
        var config = Config.INSTANCE;
        for (var whitelist : config.blockWhitelist) {
            if (blockId.toString().equals(whitelist)) {
                return false;
            }
        }
        return true;
    }

    private Boolean showBlacklist(Identifier blockId) {
        var config = Config.INSTANCE;
        for (var whitelist : config.blockBlacklist) {
            if (blockId.toString().equals(whitelist)) {
                return true;
            }
        }
        return false;
    }

    private Boolean filterBlacklist(Identifier blockId) {
        if (isFilterBlock(blockId)) {
            return false;
        }
        var config = Config.INSTANCE;
        for (var whitelist : config.blockBlacklist) {
            if (blockId.toString().equals(whitelist)) {
                return false;
            }
        }
        return true;
    }

    private void sendChat(Text text, Block block) {
        var msg = text.getString().replace("%blockName%", block.getName().getString());
        MessageUtils.addMessage(Text.translatable(msg));
    }
}
