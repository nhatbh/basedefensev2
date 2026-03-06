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

public class SpellPenaltyConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("spell_penalty.json");

    public static ConfigData data = new ConfigData();

    public static class ConfigData {
        /** How much each point of excess spell power above the threshold amplifies cast time. */
        public double spellPenaltyWeight = 1.5;
        /** Same weight applied to instant-cast spells (mana cost only). */
        public double instantSpellPenaltyWeight = 1.5;
        /** Total bonus power threshold before penalties kick in (generalPower-1 + schoolPower-1). */
        public double penaltyThreshold = 3.0;
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
            LOGGER.error("Failed to load SpellPenaltyConfig, using defaults", e);
            data = new ConfigData();
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save SpellPenaltyConfig", e);
        }
    }
}
