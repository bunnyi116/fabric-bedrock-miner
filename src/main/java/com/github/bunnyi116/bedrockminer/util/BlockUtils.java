package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.level;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;

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

    public static Identifier getKey(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    public static String getKeyString(Block block) {
        return getKey(block).toString();
    }

    public static boolean canSupportCenter(BlockPos blockPos, Direction direction) {
        return Block.canSupportCenter(level, blockPos, direction);
    }

    public static boolean canPlace(BlockPos placePos, BlockState stateToPlace, boolean checkSurvive) {
        if (!isReplaceable(level.getBlockState(placePos))) {
            //TODO: 因为破基岩没有台阶(Slabs)需求, 所以没进一步对双层方块检查
            return false;
        }
        if (checkSurvive && !stateToPlace.canSurvive(level, placePos)) {
            return false;
        }
        CollisionContext collisionContext = player != null
                ? CollisionContext.placementContext(player)
                : CollisionContext.empty();
        return level.isUnobstructed(stateToPlace, placePos, collisionContext);
    }

    public static boolean canPlace(BlockPos placePos, BlockState stateToPlace) {
        return canPlace(placePos, stateToPlace, false);
    }
}
