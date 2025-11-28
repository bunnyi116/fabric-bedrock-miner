package com.github.bunnyi116.bedrockminer.command;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.Test;
import com.github.bunnyi116.bedrockminer.command.commands.BehaviorCommand;
import com.github.bunnyi116.bedrockminer.command.commands.DebugCommand;
import com.github.bunnyi116.bedrockminer.command.commands.DisableCommand;
import com.github.bunnyi116.bedrockminer.command.commands.TaskCommand;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.ArrayList;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandManager {
    private static final ArrayList<CommandBase> commands;

    static {
        commands = new ArrayList<>();
        commands.add(new BehaviorCommand());
        commands.add(new DebugCommand());
        commands.add(new TaskCommand());
        commands.add(new DisableCommand());
    }

    public static void register() {
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal(BedrockMiner.COMMAND_PREFIX)
                .executes(context -> {
                            TaskManager.getInstance().switchToggle();
                            return Command.SINGLE_SUCCESS;
                        }
                );
        if (BedrockMiner.TEST) {
            Test.register(root);
        }
        for (CommandBase command : commands) {
            command.register(root);
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(root);
        });
//        ClientCommandManager.getActiveDispatcher()
    }
}
