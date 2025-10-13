package com.github.bunnyi116.bedrockminer.command.commands;

import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.command.CommandBase;
import com.github.bunnyi116.bedrockminer.command.argument.BlockPosArgumentType;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;

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
                .then(literal("shortWait")
                        .then(argument("isShortWait", BoolArgumentType.bool())
                                .executes(this::onWait)
                        )
                )
                .then(literal("add")
                        .then(argument("blockPos", BlockPosArgumentType.blockPos())
                                        .executes(this::add)
//                                .then(argument("blockPos2", BlockPosArgumentType.blockPos()).executes(this::selection))))
                        ))
                .then(literal("clear").executes(this::clear));

    }

    private int onWait(CommandContext<FabricClientCommandSource> context) {
        var isShortWait = BoolArgumentType.getBool(context, "isShortWait");
        if (isShortWait) {
            MessageUtils.addMessage(I18n.COMMAND_TASK_SHORT_WAIT_SHORT);
        } else {
            MessageUtils.addMessage(I18n.COMMAND_TASK_SHORT_WAIT_NORMAL);
        }
        Config.INSTANCE.taskShortWait = isShortWait;
        Config.save();
        return 0;
    }

    private int selection(CommandContext<FabricClientCommandSource> context) {
        var blockPos1 = BlockPosArgumentType.getBlockPos(context, "blockPos");
        var blockPos2 = BlockPosArgumentType.getBlockPos(context, "blockPos");
        return 0;
    }

    private Text getModeText(boolean mode, Direction... directions) {
        List<String> list = new ArrayList<>();
        for (Direction direction : directions) {
            list.add(direction.getId());
        }
        return Text.literal(String.format("%s: %s", String.join(", ", list), mode));
    }


    private int add(CommandContext<FabricClientCommandSource> context) {
        var blockPos = BlockPosArgumentType.getBlockPos(context, "blockPos");
        var client = MinecraftClient.getInstance();
        var world = client.world;
        if (world != null) {
            var blockState = world.getBlockState(blockPos);
            var block = blockState.getBlock();
            TaskManager.addTask(block, blockPos, world);
        }
        return 0;
    }

    private int clear(CommandContext<FabricClientCommandSource> context) {
        TaskManager.clearTask();
        return 0;
    }
}
