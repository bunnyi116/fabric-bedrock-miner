package yan.lx.bedrockminer.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import yan.lx.bedrockminer.LanguageText;
import yan.lx.bedrockminer.command.CommandBase;
import yan.lx.bedrockminer.config.Config;
import yan.lx.bedrockminer.utils.MessageUtils;

public class DisableCommand extends CommandBase {

    @Override
    public String getName() {
        return "disable";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder, CommandRegistryAccess registryAccess) {
        builder.executes(context -> toggleSwitch());
    }

    private int toggleSwitch() {
        if (Config.INSTANCE.disable) {
            Config.INSTANCE.disable = false;
            MessageUtils.addMessage(LanguageText.COMMAND_DISABLE_OFF);
        } else {
            Config.INSTANCE.disable = true;
            MessageUtils.addMessage(LanguageText.COMMAND_DISABLE_ON);
        }
        Config.save();
        return 0;
    }
}
