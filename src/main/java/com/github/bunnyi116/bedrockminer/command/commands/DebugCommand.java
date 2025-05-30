package com.github.bunnyi116.bedrockminer.command.commands;

import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.command.CommandBase;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

public class DebugCommand extends CommandBase {

    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder, CommandRegistryAccess registryAccess) {
        builder.executes(context -> {
                    toggleSwitch(!Config.INSTANCE.debug);
                    return 1;
                })
                .then(argument("bool", BoolArgumentType.bool()).executes(this::toggleSwitch));
    }

    private int toggleSwitch(CommandContext<FabricClientCommandSource> context) {
        boolean b = BoolArgumentType.getBool(context, "bool");
        toggleSwitch(b);
        return 1;
    }

    private void toggleSwitch(boolean b) {
        if (b) {
            MessageUtils.addMessage(I18n.DEBUG_ON);
        } else {
            MessageUtils.addMessage(I18n.DEBUG_OFF);
        }
        Config.INSTANCE.debug = b;
        Config.save();
    }
}
