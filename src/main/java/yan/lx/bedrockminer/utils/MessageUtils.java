package yan.lx.bedrockminer.utils;

import net.minecraft.text.Text;

import static yan.lx.bedrockminer.BedrockMiner.mc;

public class MessageUtils {
    public static void setOverlayMessage(Text message) {
        mc.inGameHud.setOverlayMessage(message, false);
    }

    public static void addMessage(Text message) {
        mc.inGameHud.getChatHud().addMessage(message);
    }
}
