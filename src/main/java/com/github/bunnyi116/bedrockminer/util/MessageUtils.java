package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.text.Text;

import static com.github.bunnyi116.bedrockminer.Mod.client;

public class MessageUtils {
    public static void setOverlayMessage(Text message) {
        client.inGameHud.setOverlayMessage(message, false);
    }

    public static void addMessage(Text message) {
        client.inGameHud.getChatHud().addMessage(message);
    }
}
