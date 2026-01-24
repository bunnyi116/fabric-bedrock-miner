package com.github.bunnyi116.bedrockminer.task;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class TaskRegion {
    public String name;
    public String dimensionId;
    public BlockPos pos1;
    public BlockPos pos2;

    public TaskRegion(String name, Level world, BlockPos pos1, BlockPos pos2) {
        this.name = name;
        this.dimensionId = world.dimension().identifier().toString();
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public boolean isForWorld(Level world) {
        return this.dimensionId.equals(world.dimension().identifier().toString());
    }

    public BoundingBox getBlockBox() {
        return BoundingBox.fromCorners(pos1, pos2);
    }
}
