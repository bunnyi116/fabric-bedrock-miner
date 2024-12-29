package yan.lx.bedrockminer.command.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Block;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import yan.lx.bedrockminer.LanguageText;
import yan.lx.bedrockminer.command.CommandBase;
import yan.lx.bedrockminer.command.argument.BlockArgument;
import yan.lx.bedrockminer.command.argument.operator.OperatorArgument;
import yan.lx.bedrockminer.config.Config;
import yan.lx.bedrockminer.utils.MessageUtils;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import static yan.lx.bedrockminer.utils.BlockUtils.*;

public class BehaviorCommand extends CommandBase {
    @Override
    public String getName() {
        return "behavior";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder, CommandRegistryAccess registryAccess) {
        builder.then(literal("floor")
                        .then(literal("add")
                                .then(argument("floor", IntegerArgumentType.integer())
                                        .executes(this::addFloor)
                                )

                        )
                        .then(literal("remove")
                                .then(argument("floor", IntegerArgumentType.integer())
                                        .executes(this::removeFloor)
                                )
                        )
                )

                .then(literal("block")
                        .then(literal("whitelist")
                                .then(literal("add")
                                        .then(argument("block", new BlockArgument(this::filterWhitelist))
                                                .executes(context -> addBlock(context, true))
                                        )
                                )
                                .then(literal("remove")
                                        .then(argument("block", new BlockArgument(this::showWhitelist))
                                                .executes(context -> removeBlock(context, true))
                                        )
                                ))
                        .then(literal("blacklist")
                                .then(literal("add")
                                        .then(argument("block", new BlockArgument(this::filterBlacklist))
                                                .executes(context -> addBlock(context, false))
                                        )
                                )
                                .then(literal("remove")
                                        .then(argument("block", new BlockArgument(this::showBlacklist))
                                                .executes(context -> removeBlock(context, false))
                                        )
                                )
                        )
                );
    }

    private int addFloor(CommandContext<FabricClientCommandSource> context) {
        var floor = IntegerArgumentType.getInteger(context, "floor");
        var config = Config.INSTANCE;
        if (!config.floorsBlacklist.contains(floor)) {
            config.floorsBlacklist.add(floor);
            Config.save();
        }
        MessageUtils.addMessage(
                Text.literal(LanguageText.FLOOR_BLACK_LIST_ADD.getString()
                        .replace("%count%", String.valueOf(floor))
                )
        );
        return 0;
    }

    private int removeFloor(CommandContext<FabricClientCommandSource> context) {
        var floor = IntegerArgumentType.getInteger(context, "floor");
        var config = Config.INSTANCE;
        if (config.floorsBlacklist.contains(floor)) {
            config.floorsBlacklist.remove((Integer) floor);
            Config.save();
        }
        MessageUtils.addMessage(
                Text.literal(LanguageText.FLOOR_BLACK_LIST_REMOVE.getString()
                        .replace("%count%", String.valueOf(floor))
                )
        );
        return 0;
    }

    private int addBlock(CommandContext<FabricClientCommandSource> context, boolean whitelist) {
        var block = BlockArgument.getBlock(context, "block");
        var config = Config.INSTANCE;
        var blockId = getBlockId(block);
        if (whitelist && !config.blockWhitelist.contains(blockId)) {
            config.blockWhitelist.add(blockId);
            Config.save();
            sendChat(LanguageText.COMMAND_BLOCK_WHITELIST_ADD, block);
        } else if (!config.blockBlacklist.contains(blockId)) {
            config.blockBlacklist.add(blockId);
            Config.save();
            sendChat(LanguageText.COMMAND_BLOCK_BLACKLIST_ADD, block);
        }
        return 0;
    }

    private int removeBlock(CommandContext<FabricClientCommandSource> context, boolean whitelist) {
        var block = BlockArgument.getBlock(context, "block");
        var config = Config.INSTANCE;
        var blockId = getBlockId(block);
        if (whitelist && config.blockWhitelist.contains(blockId)) {
            config.blockWhitelist.remove(blockId);
            Config.save();
            sendChat(LanguageText.COMMAND_BLOCK_WHITELIST_REMOVE, block);
        } else if (config.blockBlacklist.contains(blockId)) {
            config.blockBlacklist.remove(blockId);
            Config.save();
            sendChat(LanguageText.COMMAND_BLOCK_BLACKLIST_REMOVE, block);
        }
        return 0;
    }

    private boolean isFilterBlock(Block block) {
        return block.getDefaultState().isAir() || block.getDefaultState().isReplaceable();
    }

    private Boolean filterBlocks(Block block, boolean isWhitelist, boolean isBlacklist) {
        if (isFilterBlock(block)) {
            return true;
        }
        if (isWhitelist) {
            return Config.INSTANCE.blockWhitelist.contains(getBlockId(block));
        }
        if (isBlacklist) {
            return Config.INSTANCE.blockBlacklist.contains(getBlockId(block));
        }
        return false;
    }

    private Boolean showBlocks(Block block, boolean isWhitelist, boolean isBlacklist) {
        if (isWhitelist) {
            return !Config.INSTANCE.blockWhitelist.contains(getBlockId(block));
        }
        if (isBlacklist) {
            return !Config.INSTANCE.blockBlacklist.contains(getBlockId(block));
        }
        return false;
    }

    private Boolean filterWhitelist(Block block) {
        return filterBlocks(block, true, false);
    }

    private Boolean filterBlacklist(Block block) {
        return filterBlocks(block, false, true);
    }

    private Boolean showWhitelist(Block block) {
        return showBlocks(block, true, false);
    }

    private Boolean showBlacklist(Block block) {
        return showBlocks(block, false, true);
    }

    private void sendChat(Text text, Block block) {
        var msg = text.getString().replace("#blockName#", getBlockName(block));
        MessageUtils.addMessage(Text.literal(msg));
    }
}
