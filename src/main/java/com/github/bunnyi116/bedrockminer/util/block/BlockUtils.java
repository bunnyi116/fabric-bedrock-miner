package com.github.bunnyi116.bedrockminer.util.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.world;

public class BlockUtils {
    public static boolean isReplaceable(BlockState blockState) {
        //#if MC > 11902
        return blockState.canBeReplaced();
        //#else
        //$$ return blockState.getMaterial().isReplaceable();
        //#endif
    }

    public static @NotNull Block getBlock(Identifier blockId) {
        //#if MC > 12101
        return BuiltInRegistries.BLOCK.getValue(blockId);
        //#else
        //$$ return BuiltInRegistries.BLOCK.get(blockId);
        //#endif
    }

    public static String getBlockName(Block block) {
        return block.getName().getString();
    }

    public static Identifier getIdentifier(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    public static String getIdentifierString(Block block) {
        return getIdentifier(block).toString();
    }

    public static boolean sideCoversSmallSquare(BlockPos blockPos, Direction direction) {
        return Block.canSupportCenter(world, blockPos, direction);
    }
}
