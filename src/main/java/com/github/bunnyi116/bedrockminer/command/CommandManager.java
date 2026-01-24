package com.github.bunnyi116.bedrockminer.command;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.Test;
import com.github.bunnyi116.bedrockminer.command.commands.*;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.ArrayList;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class CommandManager {
    private static final ArrayList<CommandBase> commands;

    static {
        commands = new ArrayList<>();
        commands.add(new BehaviorCommand());
        commands.add(new DebugCommand());
        commands.add(new TaskCommand());
        commands.add(new DisableCommand());
        commands.add(new DisableEmptyHandSwitchToggleCommand());
    }

    private static String getCommandPrefix() {
        return BedrockMiner.COMMAND_PREFIX;
    }

    public static void register() {
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal(getCommandPrefix()).executes(context -> {
            TaskManager.getInstance().switchToggle();
            return Command.SINGLE_SUCCESS;
        });
        for (var command : commands) {
            command.register(root);
        }
        if (BedrockMiner.TEST) {
            Test.register(root);
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(root);
        });
    }
}
