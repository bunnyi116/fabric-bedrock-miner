package yan.lx.bedrockminer.task;

import net.minecraft.util.math.Direction;

public class TaskSeekSchemeInfo {
    public final Direction direction;
    public final TaskSeekBlockInfo piston;
    public final TaskSeekBlockInfo redstoneTorch;
    public final TaskSeekBlockInfo slimeBlock;

    public TaskSeekSchemeInfo(Direction direction, TaskSeekBlockInfo piston, TaskSeekBlockInfo redstoneTorch, TaskSeekBlockInfo slimeBlock) {
        this.direction = direction;
        this.piston = piston;
        this.redstoneTorch = redstoneTorch;
        this.slimeBlock = slimeBlock;
    }
}
