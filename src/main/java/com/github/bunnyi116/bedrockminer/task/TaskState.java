package com.github.bunnyi116.bedrockminer.task;

public enum TaskState {
    INITIALIZE,
    WAIT_GAME_UPDATE,
    WAIT_CUSTOM_UPDATE,
    FIND_PLACE,
    EXECUTE,
    TIMEOUT,
    FAIL,
    BLOCK_RECYCLING,
    COMPLETE;

    private final boolean waitGameUpdate;

    TaskState(boolean waitGameUpdate) {
        this.waitGameUpdate = waitGameUpdate;
    }

    TaskState() {
        this(false);
    }

    public boolean isWaitGameUpdate() {
        return waitGameUpdate;
    }
}

