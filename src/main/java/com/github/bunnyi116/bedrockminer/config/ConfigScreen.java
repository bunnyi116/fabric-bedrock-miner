package com.github.bunnyi116.bedrockminer.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class ConfigScreen {
    public static Screen create(Screen parent) {
        Config config = Config.getInstance();

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("bedrockminer.config.title"));

        builder.setSavingRunnable(config::save);

        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("bedrockminer.config.general"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Basic toggles
        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("bedrockminer.config.disable"), config.disable)
            .setDefaultValue(false)
            .setSaveConsumer(newValue -> config.disable = newValue)
            .build());

//        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("bedrockminer.config.simple_mode"), config.simpleMode)
//            .setDefaultValue(false)
//            .setSaveConsumer(newValue -> config.simpleMode = newValue)
//            .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("bedrockminer.config.fast_processing"), config.fast)
            .setDefaultValue(true)
            .setSaveConsumer(newValue -> config.fast = newValue)
            .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("bedrockminer.config.disable_empty_hand_toggle"), config.disableEmptyHandSwitchToggle)
            .setDefaultValue(false)
            .setSaveConsumer(newValue -> config.disableEmptyHandSwitchToggle = newValue)
            .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("bedrockminer.config.debug"), config.debug)
            .setDefaultValue(false)
            .setSaveConsumer(newValue -> config.debug = newValue)
            .build());

        general.addEntry(entryBuilder.startIntSlider(Component.translatable("bedrockminer.config.limit_max"), config.limitMax, 1, 50)
            .setDefaultValue(1)
            .setSaveConsumer(newValue -> config.limitMax = newValue)
            .build());

        // Lists
        general.addEntry(entryBuilder.startStrList(Component.translatable("bedrockminer.config.block_whitelist"), config.blockWhitelist)
            .setDefaultValue(Config.getDefaultBlockWhitelist())
            .setSaveConsumer(newValue -> config.blockWhitelist = newValue)
            .build());

        general.addEntry(entryBuilder.startIntList(Component.translatable("bedrockminer.config.floors_blacklist"), config.floorsBlacklist)
            .setDefaultValue(new ArrayList<>())
            .setSaveConsumer(newValue -> config.floorsBlacklist = newValue)
            .build());

        return builder.build();
    }
}
