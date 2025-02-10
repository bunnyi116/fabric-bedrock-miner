package com.github.bunnyi116.bedrockminer.task.block;

import net.minecraft.block.Block;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import com.github.bunnyi116.bedrockminer.task.TaskBlock;

public class TaskTargetBlock extends TaskBlock {
    public final Block block;

    public TaskTargetBlock(ClientWorld world, BlockPos pos, Block block) {
        super(world, pos);
        this.block = block;
    }
}
