package com.github.bunnyi116.bedrockminer.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public abstract class CommandBase {
    public abstract String getName();

    public abstract void build(LiteralArgumentBuilder<FabricClientCommandSource> builder);

    public final void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        LiteralArgumentBuilder<FabricClientCommandSource> builder = literal(this.getName());
        build(builder);
        root.then(builder);
    }
}
