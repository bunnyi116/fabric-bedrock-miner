package com.github.bunnyi116.bedrockminer.command.commands;

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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class TaskCommand extends CommandBase {

    @Override
    public String getName() {
        return "task";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        builder
                .then(literal("limitMax")
                        .then(argument("int", IntegerArgumentType.integer(1, 10))
                                .executes(context -> {
                                    int limitMax = IntegerArgumentType.getInteger(context, "int");
                                    MessageUtils.addMessage(Component.literal("任务同步执行限制最大值已设置为: " + limitMax));
                                    Config.getInstance().limitMax = limitMax;
                                    Config.getInstance().save();
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("fast")
                        .then(argument("bool", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean b = BoolArgumentType.getBool(context, "bool");
                                    if (b) {
                                        MessageUtils.addMessage(I18n.COMMAND_TASK_FAST_ON);
                                    } else {
                                        MessageUtils.addMessage(I18n.COMMAND_TASK_FAST_OFF);
                                    }
                                    Config.getInstance().fast = b;
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
                                    StringReader reader = new StringReader(suggestionsBuilder.getInput());
                                    reader.setCursor(suggestionsBuilder.getStart());
                                    String input = StringReaderUtils.readUnquotedString(reader);
                                    for (TaskRegion item : Config.getInstance().ranges) {
                                        if (item.name.contains(input)) {
                                            suggestionsBuilder.suggest(StringArgumentType.escapeIfRequired(item.name));
                                        }
                                    }
                                    for (TaskRegion item : TaskManager.getInstance().getPendingRegionTasks()) {
                                        if (item.name.contains(input)) {
                                            suggestionsBuilder.suggest(StringArgumentType.escapeIfRequired(item.name));
                                        }
                                    }
                                    return suggestionsBuilder.buildFuture();
                                })
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    @Nullable TaskRegion range = null;
                                    for (TaskRegion item : Config.getInstance().ranges) {
                                        if (item.name.equals(name)) {
                                            range = item;
                                            break;
                                        }
                                    }
                                    if (range == null) {
                                        for (TaskRegion item : TaskManager.getInstance().getPendingRegionTasks()) {
                                            if (item.name.equals(name)) {
                                                range = item;
                                                break;
                                            }
                                        }
                                    }
                                    if (range != null) {
                                        Config.getInstance().ranges.remove(range);
                                        Config.getInstance().save();
                                        MessageUtils.addMessage(Component.literal("已成功删除: " + name));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("clear").executes(context -> {
                    TaskManager.getInstance().removeBlockTaskAll();
                    TaskManager.getInstance().removeRegionTaskAll();
                    return Command.SINGLE_SUCCESS;
                }));

    }

    private int addRegionTask(CommandContext<FabricClientCommandSource> context, boolean isConfig) {
        String name = StringArgumentType.getString(context, "name");
        BlockPos blockPos1 = BlockPosArgumentType.getBlockPos(context, "blockPos1");
        BlockPos blockPos2 = BlockPosArgumentType.getBlockPos(context, "blockPos2");
        boolean b = true;
        if (isConfig) {
            for (TaskRegion item : Config.getInstance().ranges) {
                if (item.name.equals(name)) {
                    b = false;
                    break;
                }
            }
            if (b) {
                TaskRegion range = new TaskRegion(name, BedrockMiner.level, blockPos1, blockPos2);
                ConfigManager.getInstance().getConfig().ranges.add(range);
                ConfigManager.getInstance().getConfig().save();
                MessageUtils.addMessage(Component.literal("已成功添加到配置文件: " + name));
            }
        } else {
            for (TaskRegion item : TaskManager.getInstance().getPendingRegionTasks()) {
                if (item.name.equals(name)) {
                    b = false;
                    break;
                }
            }
            if (b) {
                TaskRegion range = new TaskRegion(name, BedrockMiner.level, blockPos1, blockPos2);
                TaskManager.getInstance().getPendingRegionTasks().add(range);
                MessageUtils.addMessage(Component.literal("已成功添加: " + name));
            }
        }
        return Command.SINGLE_SUCCESS;
    }
}
