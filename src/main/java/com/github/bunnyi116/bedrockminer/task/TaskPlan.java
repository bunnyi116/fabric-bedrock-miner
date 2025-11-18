package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.util.block.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.player.PlayerUtils;
import net.minecraft.block.Block;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.world;


public class TaskPlan {
    public final Direction direction;
    public final TaskPlanItem piston;
    public final TaskPlanItem redstoneTorch;
    public final TaskPlanItem slimeBlock;
    public int level;

    public TaskPlan(Direction direction, TaskPlanItem piston, TaskPlanItem redstoneTorch, TaskPlanItem slimeBlock) {
        this.direction = direction;
        this.piston = piston;
        this.redstoneTorch = redstoneTorch;
        this.slimeBlock = slimeBlock;
        this.level = switch (direction) {
            case UP -> 1;
            case DOWN -> 2;
            case NORTH, SOUTH, WEST, EAST -> 4;
        };
    }

    public boolean isWorldValid() {
        return World.isValid(piston.pos) && World.isValid(redstoneTorch.pos) && World.isValid(slimeBlock.pos);
    }

    public boolean canInteractWithBlockAt() {
        final var b1 = PlayerUtils.canInteractWithBlockAt(piston.pos, 1.0F);
        final var b2 = PlayerUtils.canInteractWithBlockAt(redstoneTorch.pos, 1.0F);
        if (b1 && b2) {
            final var b3 = PlayerUtils.canInteractWithBlockAt(slimeBlock.pos, 1.0F);
            if (b3 && BlockUtils.isReplaceable(world.getBlockState(slimeBlock.pos))) {
                return true;
            }
            return Block.sideCoversSmallSquare(world, slimeBlock.pos, slimeBlock.facing);
        }
        return false;
    }
}