package com.github.bunnyi116.bedrockminer.command.commands;

import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.command.CommandBase;
import com.github.bunnyi116.bedrockminer.command.argument.BlockArgument;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.BlockUtils;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import static com.github.bunnyi116.bedrockminer.util.BlockUtils.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class BehaviorCommand extends CommandBase {
    @Override
    public String getName() {
        return "behavior";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
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
                        .then(literal("list").executes(this::listFloor)
                        )
                )
                .then(literal("blockWhitelist")
                        .then(literal("add")
                                .then(argument("block", new BlockArgument(this::filterBlockWhitelist))
                                        .executes(this::addBlockWhitelist)
                                )
                        )
                        .then(literal("remove")
                                .then(argument("block", new BlockArgument(this::showBlockWhitelist))
                                        .executes(this::removeBlockWhitelist)
                                )
                        )
                );
    }

    private int listFloor(CommandContext<FabricClientCommandSource> context) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Config.getInstance().floorsBlacklist.size(); i++) {
            Integer floor = Config.getInstance().floorsBlacklist.get(i);
            if (i == Config.getInstance().floorsBlacklist.size() - 1) {
                sb.append(floor);
            } else {
                sb.append(floor).append(",");
            }
        }
        MessageUtils.addMessage(
                Component.literal(I18n.FLOOR_BLACK_LIST_SHOW.getString()
                        .replace("%listFloor%", sb.toString())
                )
        );
        return Command.SINGLE_SUCCESS;
    }

    private int addFloor(CommandContext<FabricClientCommandSource> context) {
        int floor = IntegerArgumentType.getInteger(context, "floor");
        if (!Config.getInstance().floorsBlacklist.contains(floor)) {
            Config.getInstance().floorsBlacklist.add(floor);
            Config.getInstance().save();
        }
        MessageUtils.addMessage(
                Component.literal(I18n.FLOOR_BLACK_LIST_ADD.getString()
                        .replace("%count%", String.valueOf(floor))
                )
        );
        return Command.SINGLE_SUCCESS;
    }

    private int removeFloor(CommandContext<FabricClientCommandSource> context) {
        int floor = IntegerArgumentType.getInteger(context, "floor");
        if (Config.getInstance().floorsBlacklist.contains(floor)) {
            Config.getInstance().floorsBlacklist.remove((Integer) floor);
            Config.getInstance().save();
        }
        MessageUtils.addMessage(
                Component.literal(I18n.FLOOR_BLACK_LIST_REMOVE.getString()
                        .replace("%count%", String.valueOf(floor))
                )
        );
        return Command.SINGLE_SUCCESS;
    }

    private int addBlockWhitelist(CommandContext<FabricClientCommandSource> context) {
        Block[] blocks = BlockArgument.getBlock(context, "block");
        for (Block block : blocks) {
            String blockId = getKeyString(block);
            if (!Config.getInstance().blockWhitelist.contains(blockId)) {
                Config.getInstance().blockWhitelist.add(blockId);
                Config.getInstance().save();
                sendChat(I18n.COMMAND_BLOCK_WHITELIST_ADD, block);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int removeBlockWhitelist(CommandContext<FabricClientCommandSource> context) {
        Block[] blocks = BlockArgument.getBlock(context, "block");
        for (Block block : blocks) {
            String blockId = getKeyString(block);
            if (Config.getInstance().blockWhitelist.contains(blockId)) {
                Config.getInstance().blockWhitelist.remove(blockId);
                Config.getInstance().save();
                sendChat(I18n.COMMAND_BLOCK_WHITELIST_REMOVE, block);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private boolean isFilterBlock(Block block) {
        return block.defaultBlockState().isAir() || BlockUtils.isReplaceable(block.defaultBlockState());
    }

    private Boolean filterBlockWhitelist(Block block) {
        if (isFilterBlock(block)) {
            return false;
        }
        return !Config.getInstance().blockWhitelist.contains(getKeyString(block));
    }

    private Boolean showBlockWhitelist(Block block) {
        return Config.getInstance().blockWhitelist.contains(getKeyString(block));
    }

    private void sendChat(Component text, Block block) {
        String blockId = getKeyString(block);
        String msg = text.getString().replace("#blockName#", String.format("%s(%s)", getBlockName(block), blockId));
        MessageUtils.addMessage(Component.literal(msg));
    }
}
