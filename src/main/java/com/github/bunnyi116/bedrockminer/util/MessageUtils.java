package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.network.chat.Component;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.client;

public class MessageUtils {
    public static void setOverlayMessage(Component message) {
        client.gui.setOverlayMessage(message, false);
    }

    public static void addMessage(Component message) {
        //#if MC>=260000
        client.gui.getChat().addClientSystemMessage(message);
        //#else
        //$$ client.gui.getChat().addMessage(message);
        //#endif
    }
}
