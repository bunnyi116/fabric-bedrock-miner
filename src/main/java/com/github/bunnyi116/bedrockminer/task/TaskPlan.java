package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.util.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.PlayerUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;


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
        return Level.isInSpawnableBounds(piston.pos) && Level.isInSpawnableBounds(redstoneTorch.pos) && Level.isInSpawnableBounds(slimeBlock.pos);
    }

    public boolean canInteractWithBlockAt() {
        final var b1 = PlayerUtils.canInteractWithBlockAt(piston.pos, 0F);
        final var b2 = PlayerUtils.canInteractWithBlockAt(redstoneTorch.pos, 0F);
        if (b1 && b2) {
            final var b3 = PlayerUtils.canInteractWithBlockAt(slimeBlock.pos, 0F);
            if (b3 && BlockUtils.isReplaceable(BedrockMiner.level.getBlockState(slimeBlock.pos))) {
                return true;
            }
            return  Block.canSupportCenter(BedrockMiner.level, slimeBlock.pos, slimeBlock.facing);
        }
        return false;
    }
}