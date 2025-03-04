package com.github.bunnyi116.bedrockminer.task;

public enum TaskState {
    INITIALIZE,
    WAIT_GAME_UPDATE,
    WAIT_CUSTOM,
    FIND_PISTON,
    FIND_REDSTONE_TORCH,
    FIND_SLIME_BLOCK,

    PLACE_PISTON,
    PLACE_REDSTONE_TORCH,
    PLACE_SLIME_BLOCK,
    EXECUTE,
    TIMEOUT,
    FAIL,
    RECYCLED_ITEMS,
    COMPLETE
}
