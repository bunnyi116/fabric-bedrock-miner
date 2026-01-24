package com.github.bunnyi116.bedrockminer.command.commands;

import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.command.CommandBase;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;

public class DisableEmptyHandSwitchToggleCommand extends CommandBase {

    @Override
    public String getName() {
        return "disableEmptyHandSwitchToggle";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        builder.executes(context -> {
                    if (Config.getInstance().disableEmptyHandSwitchToggle) {
                        Config.getInstance().disableEmptyHandSwitchToggle = false;
                        MessageUtils.addMessage(I18n.COMMAND_DISABLE_OFF);
                    } else {
                        Config.getInstance().disableEmptyHandSwitchToggle = true;
                        MessageUtils.addMessage(I18n.COMMAND_DISABLE_ON);
                    }
                    Config.getInstance().save();
                    return Command.SINGLE_SUCCESS;
                })
                .then(argument("bool", BoolArgumentType.bool())
                        .executes(context -> {
                            Config.getInstance().disableEmptyHandSwitchToggle = BoolArgumentType.getBool(context, "bool");
                            if (Config.getInstance().disableEmptyHandSwitchToggle) {
                                MessageUtils.addMessage(I18n.COMMAND_DISABLE_ON);
                            } else {
                                MessageUtils.addMessage(I18n.COMMAND_DISABLE_OFF);
                            }
                            Config.getInstance().save();
                            return Command.SINGLE_SUCCESS;
                        })
                );
    }

}
