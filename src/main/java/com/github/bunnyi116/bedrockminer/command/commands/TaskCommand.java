package com.github.bunnyi116.bedrockminer.command.commands;

import com.github.bunnyi116.bedrockminer.APIs;
import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.command.CommandBase;
import com.github.bunnyi116.bedrockminer.command.argument.BlockPosArgumentType;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.config.ConfigManager;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.github.bunnyi116.bedrockminer.task.TaskRegion;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.StringReaderUtils;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TaskCommand extends CommandBase {

    @Override
    public String getName() {
        return "task";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder, CommandRegistryAccess registryAccess) {
        builder
                .then(literal("short")
                        .then(argument("bool", BoolArgumentType.bool())
                                .executes(context -> {
                                    var b = BoolArgumentType.getBool(context, "bool");
                                    if (b) {
                                        MessageUtils.addMessage(I18n.COMMAND_TASK_SHORT_WAIT_SHORT);
                                    } else {
                                        MessageUtils.addMessage(I18n.COMMAND_TASK_SHORT_WAIT_NORMAL);
                                    }
                                    Config.getInstance().shortTsk = b;
                                    Config.getInstance().save();
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                .then(literal("add")
                        .then(argument("name", StringArgumentType.string())
                                .then(argument("blockPos1", BlockPosArgumentType.blockPos())
                                        .then(argument("blockPos2", BlockPosArgumentType.blockPos())
                                                .executes(context -> addRegionTask(context, false))
                                        )
                                )
                        )
                )

                .then(literal("addToConfig")
                        .then(argument("name", StringArgumentType.string())
                                .then(argument("blockPos1", BlockPosArgumentType.blockPos())
                                        .then(argument("blockPos2", BlockPosArgumentType.blockPos())
                                                .executes(context -> addRegionTask(context, true))
                                        )
                                )
                        )
                )

                .then(literal("remove")
                        .then(argument("name", StringArgumentType.string())
                                .suggests((context, suggestionsBuilder) -> {
                                    var reader = new StringReader(suggestionsBuilder.getInput());
                                    reader.setCursor(suggestionsBuilder.getStart());
                                    var input = StringReaderUtils.readUnquotedString(reader);
                                    for (var item : Config.getInstance().ranges) {
                                        if (item.name.contains(input)) {
                                            suggestionsBuilder.suggest(StringArgumentType.escapeIfRequired(item.name));
                                        }
                                    }
                                    for (var item : TaskManager.getInstance().getPendingRegionTasks()) {
                                        if (item.name.contains(input)) {
                                            suggestionsBuilder.suggest(StringArgumentType.escapeIfRequired(item.name));
                                        }
                                    }
                                    return suggestionsBuilder.buildFuture();
                                })
                                .executes(context -> {
                                    final var name = StringArgumentType.getString(context, "name");
                                    @Nullable TaskRegion range = null;
                                    for (final var item : Config.getInstance().ranges) {
                                        if (item.name.equals(name)) {
                                            range = item;
                                            break;
                                        }
                                    }
                                    if (range == null) {
                                        for (final var item : TaskManager.getInstance().getPendingRegionTasks()) {
                                            if (item.name.equals(name)) {
                                                range = item;
                                                break;
                                            }
                                        }
                                    }
                                    if (range != null) {
                                        Config.getInstance().ranges.remove(range);
                                        Config.getInstance().save();
                                        MessageUtils.addMessage(Text.literal("已成功删除: " + name));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                .then(literal("clear").executes(context -> {
                    APIs.getInstance().getTaskManager().removeBlockTaskAll();
                    APIs.getInstance().getTaskManager().removeRegionTaskAll();
                    return Command.SINGLE_SUCCESS;
                }));

    }

    private int addRegionTask(CommandContext<FabricClientCommandSource> context, boolean isConfig) {
        final var name = StringArgumentType.getString(context, "name");
        final var blockPos1 = BlockPosArgumentType.getBlockPos(context, "blockPos1");
        final var blockPos2 = BlockPosArgumentType.getBlockPos(context, "blockPos2");
        boolean b = true;
        if (isConfig) {
            for (final var item : ConfigManager.getInstance().getConfig().ranges) {
                if (item.name.equals(name)) {
                    b = false;
                    break;
                }
            }
            if (b) {
                final var range = new TaskRegion(name, BedrockMiner.world, blockPos1, blockPos2);
                ConfigManager.getInstance().getConfig().ranges.add(range);
                ConfigManager.getInstance().getConfig().save();
                MessageUtils.addMessage(Text.literal("已成功添加到配置文件: " + name));
            }
        } else {
            for (final var item : TaskManager.getInstance().getPendingRegionTasks()) {
                if (item.name.equals(name)) {
                    b = false;
                    break;
                }
            }
            if (b) {
                final var range = new TaskRegion(name, BedrockMiner.world, blockPos1, blockPos2);
                TaskManager.getInstance().getPendingRegionTasks().add(range);
                MessageUtils.addMessage(Text.literal("已成功添加: " + name));
            }
        }

        return Command.SINGLE_SUCCESS;
    }
}
