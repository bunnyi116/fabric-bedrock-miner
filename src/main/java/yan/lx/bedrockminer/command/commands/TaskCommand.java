package yan.lx.bedrockminer.command.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import yan.lx.bedrockminer.LanguageText;
import yan.lx.bedrockminer.command.CommandBase;
import yan.lx.bedrockminer.command.argument.BlockPosArgumentType;
import yan.lx.bedrockminer.config.Config;
import yan.lx.bedrockminer.task.TaskManager;
import yan.lx.bedrockminer.utils.MessageUtils;

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
                .then(literal("vertical")
                        .then(argument("bool", BoolArgumentType.bool()).executes(context -> {
                            Config.INSTANCE.vertical = BoolArgumentType.getBool(context, "bool");
                            MessageUtils.addMessage(Text.translatable(String.valueOf(Config.INSTANCE.vertical)));
                            Config.save();
                            return 0;
                        })))
                .then(literal("horizontal")
                        .then(argument("bool", BoolArgumentType.bool()).executes(context -> {
                            Config.INSTANCE.horizontal = BoolArgumentType.getBool(context, "bool");
                            MessageUtils.addMessage(Text.translatable(String.valueOf(Config.INSTANCE.horizontal)));
                            Config.save();
                            return 0;
                        })))
                .then(literal("add")
                        .then(argument("blockPos", BlockPosArgumentType.blockPos())
                                        .executes(this::add)
//                                .then(argument("blockPos2", BlockPosArgumentType.blockPos()).executes(this::selection))))
                        ))
                .then(literal("clear").executes(this::clear));
//
//                .then(literal("limit")
//                        .then(argument("limit", IntegerArgumentType.integer(1, 5))
//                                .executes(this::toggleSwitch)));
    }

    private int selection(CommandContext<FabricClientCommandSource> context) {
        var blockPos1 = BlockPosArgumentType.getBlockPos(context, "blockPos");
        var blockPos2 = BlockPosArgumentType.getBlockPos(context, "blockPos");
        return 0;
    }

    private Text getModeText(boolean mode, Direction... directions) {
        List<String> list = new ArrayList<>();
        for (Direction direction : directions) {
            list.add(direction.getName());
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

    private int toggleSwitch(CommandContext<FabricClientCommandSource> context) {
        var config = Config.INSTANCE;
        var limit = IntegerArgumentType.getInteger(context, "limit");
        if (config.taskLimit != limit) {
            config.taskLimit = limit;
            Config.save();
        }
        var msg = LanguageText.COMMAND_TASK_LIMIT.getString().replace("%limit%", String.valueOf(limit));
        MessageUtils.addMessage(Text.translatable(msg));
        return 0;
    }
}
