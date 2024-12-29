package yan.lx.bedrockminer.utils;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

public class BlockUtils {
    public static @NotNull Block getBlock(Identifier blockId) {
        return Registries.BLOCK.get(blockId);
    }

    public static String getBlockName(Block block) {
        return block.getName().getString();
    }

    public static Identifier getIdentifier(Block block) {
        return Registries.BLOCK.getId(block);
    }

    public static String getBlockId(Block block) {
        return getIdentifier(block).toString();
    }
}
