package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.network.chat.Component;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.minecraft;

public class MessageUtils {
    public static void setOverlayMessage(Component message) {
        //#if MC>=260200
        minecraft.gui.hud.setOverlayMessage(message, false);
        //$$ minecraft.gui.setOverlayMessage(message, false);
        //#endif
    }

    public static void addMessage(Component message) {
        //#if MC>=260200
        minecraft.gui.hud.getChat().addClientSystemMessage(message);
        //#elseif MC>=260100 && MC<260200
        //$$ minecraft.gui.getChat().addClientSystemMessage(message);
        //#else
        //$$ minecraft.gui.getChat().addMessage(message);
        //#endif
    }
}

