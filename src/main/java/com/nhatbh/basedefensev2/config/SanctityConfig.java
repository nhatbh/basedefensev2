package com.nhatbh.basedefensev2.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class SanctityConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("sanctity_config.json");

    public static ConfigData data = new ConfigData();

    public static class ConfigData {
        /** Maximum amount of sanctity. The world starts at this value. */
        public int maxSanctity = 500;
        /** Maximum amount of grace. */
        public int maxGrace = 100;
        /** Amount of grace regenerated per tick (default 0.05 per tick, or 1 every 20 ticks). */
        public double graceRegenRate = 0.05;
    }

    public static void load() {
        File configFile = CONFIG_PATH.toFile();
        if (!configFile.exists()) {
            save();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
            if (loaded != null) {
                data = loaded;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load SanctityConfig, using defaults", e);
            data = new ConfigData();
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save SanctityConfig", e);
        }
    }
}
