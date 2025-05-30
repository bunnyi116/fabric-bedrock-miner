package com.github.bunnyi116.bedrockminer;

import com.github.bunnyi116.bedrockminer.config.Config;

public class Debug {
    public static void alwaysWrite(String var1, Object... var2) {
        Mod.LOGGER.info(var1, var2);
    }

    public static void alwaysWrite(Object obj) {
        Mod.LOGGER.info(obj.toString());
    }

    public static void alwaysWrite() {
        alwaysWrite("");
    }


    public static void write(String var1, Object... var2) {
        if (Config.INSTANCE.debug) {
            Mod.LOGGER.info(var1, var2);
        }
    }

    public static void write(Object obj) {
        if (Config.INSTANCE.debug) {
            Mod.LOGGER.info(obj.toString());
        }
    }

    public static void write() {
        write("");
    }
}
