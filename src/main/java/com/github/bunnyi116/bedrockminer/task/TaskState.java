package com.github.bunnyi116.bedrockminer.task;

public enum TaskState {
    INITIALIZE,
    WAIT_GAME_UPDATE,
    WAIT_CUSTOM,
    FIND,
    PLACE_PISTON,
    PLACE_REDSTONE_TORCH,
    PLACE_SLIME_BLOCK,
    EXECUTE,
    RETRY,
    TIMEOUT,
    FAIL,
    RECYCLED_ITEMS,
    COMPLETE;
}
