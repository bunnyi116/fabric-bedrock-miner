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
        switch (direction) {
            case UP:
                this.level = 1;
                break;
            case DOWN:
                this.level = 2;
                break;
            case NORTH:
            case SOUTH:
            case WEST:
            case EAST:
                this.level = 4;
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    public boolean isWorldValid() {
        return World.isValid(piston.pos) && World.isValid(redstoneTorch.pos) && World.isValid(slimeBlock.pos);
    }

    public boolean canInteractWithBlockAt() {
        final boolean b1 = PlayerUtils.canInteractWithBlockAt(piston.pos, 0F);
        final boolean b2 = PlayerUtils.canInteractWithBlockAt(redstoneTorch.pos, 0F);
        if (b1 && b2) {
            final boolean b3 = PlayerUtils.canInteractWithBlockAt(slimeBlock.pos, 0F);
            if (b3 && BlockUtils.isReplaceable(world.getBlockState(slimeBlock.pos))) {
                return true;
            }
            return Block.sideCoversSmallSquare(world, slimeBlock.pos, slimeBlock.facing);
        }
        return false;
    }
}