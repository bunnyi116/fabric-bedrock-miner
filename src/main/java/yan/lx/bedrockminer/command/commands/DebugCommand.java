package yan.lx.bedrockminer.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import yan.lx.bedrockminer.LanguageText;
import yan.lx.bedrockminer.command.CommandBase;
import yan.lx.bedrockminer.config.Config;
import yan.lx.bedrockminer.utils.MessageUtils;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class DebugCommand extends CommandBase {

    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder, CommandRegistryAccess registryAccess) {
        builder.then(literal("true").executes(context -> toggleSwitch(true)))
                .then(literal("false").executes(context -> toggleSwitch(false)));
    }

    private int toggleSwitch(boolean b) {
        if (b) {
            MessageUtils.addMessage(LanguageText.DEBUG_ON);
        } else {
            MessageUtils.addMessage(LanguageText.DEBUG_OFF);
        }
        Config.INSTANCE.debug = b;
        Config.save();
        return 0;
    }
}
