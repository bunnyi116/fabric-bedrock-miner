package com.github.bunnyi116.bedrockminer.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class ModMenuIntegration implements ModMenuApi {
    private static Screen getScreen() {
        //#if MC > 260100
        return Minecraft.getInstance().gui.screen();
        //#else
        //$$ return Minecraft.getInstance().screen;
        //#endif
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return ConfigScreen::create;
        }
        return null;
    }
}
