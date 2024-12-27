package yan.lx.bedrockminer;

import yan.lx.bedrockminer.config.Config;

public class Debug {
    public static void alwaysWrite(String var1, Object... var2) {
        BedrockMiner.LOGGER.info(var1, var2);
    }

    public static void alwaysWrite(Object obj) {
        BedrockMiner.LOGGER.info(obj.toString());
    }

    public static void alwaysWrite() {
        alwaysWrite("");
    }


    public static void write(String var1, Object... var2) {
        if (Config.INSTANCE.debug) {
            BedrockMiner.LOGGER.info(var1, var2);
        }
    }

    public static void write(Object obj) {
        if (Config.INSTANCE.debug) {
            BedrockMiner.LOGGER.info(obj.toString());
        }
    }

    public static void write() {
        write("");
    }
}
