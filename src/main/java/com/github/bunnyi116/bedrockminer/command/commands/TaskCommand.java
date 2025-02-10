package com.github.bunnyi116.bedrockminer.command.commands;

import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.command.CommandBase;
import com.github.bunnyi116.bedrockminer.command.argument.BlockPosArgumentType;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;

import java.util.ArrayList;
import java.util.List;

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
                .then(literal("vertical")
                        .then(argument("bool", BoolArgumentType.bool()).executes(context -> {
                            Config.INSTANCE.vertical = BoolArgumentType.getBool(context, "bool");
                            MessageUtils.addMessage(Text.translatable(String.valueOf(Config.INSTANCE.vertical)));
                            Config.save();
                            return 0;
                        })))
                .then(literal("horizontal")
                        .then(argument("bool", BoolArgumentType.bool()).executes(context -> {
                            Config.INSTANCE.horizontal = BoolArgumentType.getBool(context, "bool");
                            MessageUtils.addMessage(Text.translatable(String.valueOf(Config.INSTANCE.horizontal)));
                            Config.save();
                            return 0;
                        })));

    }

}
