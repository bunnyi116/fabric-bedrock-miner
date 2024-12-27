package yan.lx.bedrockminer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import yan.lx.bedrockminer.BedrockMiner;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public abstract class CommandBase {
    public abstract String getName();

    public abstract void build(LiteralArgumentBuilder<FabricClientCommandSource> builder, CommandRegistryAccess registryAccess);

    public final void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        var builder = literal(this.getName());
        build(builder, registryAccess);
        dispatcher.register(literal(BedrockMiner.COMMAND_PREFIX).then(builder));
    }
}
