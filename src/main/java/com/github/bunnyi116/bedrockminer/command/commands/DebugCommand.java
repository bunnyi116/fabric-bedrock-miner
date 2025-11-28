package com.github.bunnyi116.bedrockminer.command.commands;

import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.command.CommandBase;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

public class DebugCommand extends CommandBase {

    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        builder
                .executes(context -> {
                    final boolean b = !Config.getInstance().debug;
                    if (b) {
                        MessageUtils.addMessage(I18n.DEBUG_ON);
                    } else {
                        MessageUtils.addMessage(I18n.DEBUG_OFF);
                    }
                    Config.getInstance().debug = b;
                    Config.getInstance().save();
                    return Command.SINGLE_SUCCESS;
                })

                .then(argument("bool", BoolArgumentType.bool())
                        .executes(context -> {
                            final boolean b = BoolArgumentType.getBool(context, "bool");
                            if (b) {
                                MessageUtils.addMessage(I18n.DEBUG_ON);
                            } else {
                                MessageUtils.addMessage(I18n.DEBUG_OFF);
                            }
                            Config.getInstance().debug = b;
                            Config.getInstance().save();
                            return Command.SINGLE_SUCCESS;
                        })
                );
    }
}
