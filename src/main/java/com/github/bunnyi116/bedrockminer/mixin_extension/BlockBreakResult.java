package com.github.bunnyi116.bedrockminer.mixin_extension;

// 方块破坏结果枚举
public enum BlockBreakResult {
    COMPLETED,      // 破坏完成
    IN_PROGRESS,    // 正在破坏，需要继续tick
    PENDING,        // 待处理
    ABORTED,        // 破坏被中止（切换方块等）
    FULL,           // 破坏位已满
    FAILED          // 破坏失败（无权限/超出边界/无法交互等）
}
