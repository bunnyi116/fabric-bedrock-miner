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
            .setTitle(Component.literal("Bedrock Miner Configuration"));

        builder.setSavingRunnable(config::save);

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Basic toggles
        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Disable Mod"), config.disable)
            .setDefaultValue(false)
            .setSaveConsumer(newValue -> config.disable = newValue)
            .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Simple Mode"), config.simpleMode)
            .setDefaultValue(false)
            .setSaveConsumer(newValue -> config.simpleMode = newValue)
            .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Short Task Processing"), config.shortTsk)
            .setDefaultValue(true)
            .setSaveConsumer(newValue -> config.shortTsk = newValue)
            .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Disable Empty Hand Toggle"), config.disableEmptyHandSwitchToggle)
            .setDefaultValue(false)
            .setSaveConsumer(newValue -> config.disableEmptyHandSwitchToggle = newValue)
            .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Debug Mode"), config.debug)
            .setDefaultValue(false)
            .setSaveConsumer(newValue -> config.debug = newValue)
            .build());

        general.addEntry(entryBuilder.startIntSlider(Component.literal("Limit Max"), config.limitMax, 1, 50)
            .setDefaultValue(1)
            .setSaveConsumer(newValue -> config.limitMax = newValue)
            .build());

        // Lists
        general.addEntry(entryBuilder.startStrList(Component.literal("Block Whitelist"), config.blockWhitelist)
            .setDefaultValue(Config.getDefaultBlockWhitelist())
            .setSaveConsumer(newValue -> config.blockWhitelist = newValue)
            .build());

        general.addEntry(entryBuilder.startIntList(Component.literal("Floors Blacklist"), config.floorsBlacklist)
            .setDefaultValue(new ArrayList<>())
            .setSaveConsumer(newValue -> config.floorsBlacklist = newValue)
            .build());

        return builder.build();
    }
}
