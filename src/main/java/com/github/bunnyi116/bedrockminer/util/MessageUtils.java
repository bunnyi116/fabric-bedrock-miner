package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.network.chat.Component;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.minecraft;

public class MessageUtils {
    private static final Component PREFIX = Component.literal("§8[§bBedrockMiner§8] §r");

    public static void setOverlayMessage(Component message) {
        //#if MC>=260200
        minecraft.gui.hud.setOverlayMessage(message, false);
        //#else
        //$$ minecraft.gui.setOverlayMessage(message, false);
        //#endif
    }

    public static void addMessage(Component message) {
        Component finalMessage = Component.empty().append(PREFIX).append(message);
        //#if MC>=260200
        minecraft.gui.hud.getChat().addClientSystemMessage(finalMessage);
        //#elseif MC>=260100 && MC<260200
        //$$ minecraft.gui.getChat().addClientSystemMessage(finalMessage);
        //#else
        //$$ minecraft.gui.getChat().addMessage(finalMessage);
        //#endif
    }
}

