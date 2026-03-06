package com.nhatbh.basedefensev2.elemental;

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
import java.util.HashMap;
import java.util.Map;

public class MobElementConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("elements.json");

    public static ConfigData data = new ConfigData();

    public static class ConfigData {
        public boolean enabled = true;
        public Map<String, String> entity_element_mappings = new HashMap<>();
    }

    public static void load() {
        File configFile = CONFIG_PATH.toFile();
        if (!configFile.exists()) {
            generateDefaultConfig();
            save();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            data = GSON.fromJson(reader, ConfigData.class);
            if (data == null) {
                data = new ConfigData();
            }
            if (data.entity_element_mappings == null) {
                data.entity_element_mappings = new HashMap<>();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load MobElementConfig", e);
            generateDefaultConfig();
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save MobElementConfig", e);
        }
    }

    private static void generateDefaultConfig() {
        data.enabled = true;
        data.entity_element_mappings.put("irons_spellbooks:apothecarist", "NATURE");
        data.entity_element_mappings.put("irons_spellbooks:archevoker", "EVOCATION");
        data.entity_element_mappings.put("irons_spellbooks:catacombs_zombie", "BLOOD");
        data.entity_element_mappings.put("irons_spellbooks:citadel_keeper", "HOLY");
        data.entity_element_mappings.put("irons_spellbooks:cryomancer", "ICE");
        data.entity_element_mappings.put("irons_spellbooks:cultist", "BLOOD");
        data.entity_element_mappings.put("irons_spellbooks:dead_king", "BLOOD");
        data.entity_element_mappings.put("irons_spellbooks:debug_wizard", "EVOCATION");
        data.entity_element_mappings.put("irons_spellbooks:magehunter_vindicator", "EVOCATION");
        data.entity_element_mappings.put("irons_spellbooks:necromancer", "BLOOD");
        data.entity_element_mappings.put("irons_spellbooks:pyromancer", "FIRE");
        data.entity_element_mappings.put("irons_spellbooks:summoned_skeleton", "BLOOD");
        data.entity_element_mappings.put("irons_spellbooks:summoned_zombie", "BLOOD");
        data.entity_element_mappings.put("minecraft:blaze", "FIRE");
        data.entity_element_mappings.put("minecraft:cave_spider", "NATURE");
        data.entity_element_mappings.put("minecraft:creeper", "LIGHTNING");
        data.entity_element_mappings.put("minecraft:drowned", "AQUA");
        data.entity_element_mappings.put("minecraft:elder_guardian", "AQUA");
        data.entity_element_mappings.put("minecraft:ender_dragon", "ENDER");
        data.entity_element_mappings.put("minecraft:enderman", "ENDER");
        data.entity_element_mappings.put("minecraft:endermite", "ENDER");
        data.entity_element_mappings.put("minecraft:evoker", "EVOCATION");
        data.entity_element_mappings.put("minecraft:ghast", "FIRE");
        data.entity_element_mappings.put("minecraft:giant", "BLOOD");
        data.entity_element_mappings.put("minecraft:guardian", "AQUA");
        data.entity_element_mappings.put("minecraft:hoglin", "NATURE");
        data.entity_element_mappings.put("minecraft:husk", "FIRE");
        data.entity_element_mappings.put("minecraft:illusioner", "EVOCATION");
        data.entity_element_mappings.put("minecraft:magma_cube", "FIRE");
        data.entity_element_mappings.put("minecraft:phantom", "LIGHTNING");
        data.entity_element_mappings.put("minecraft:piglin", "FIRE");
        data.entity_element_mappings.put("minecraft:piglin_brute", "FIRE");
        data.entity_element_mappings.put("minecraft:pillager", "EVOCATION");
        data.entity_element_mappings.put("minecraft:ravager", "EVOCATION");
        data.entity_element_mappings.put("minecraft:shulker", "ENDER");
        data.entity_element_mappings.put("minecraft:silverfish", "NATURE");
        data.entity_element_mappings.put("minecraft:skeleton", "ICE");
        data.entity_element_mappings.put("minecraft:slime", "NATURE");
        data.entity_element_mappings.put("minecraft:spider", "NATURE");
        data.entity_element_mappings.put("minecraft:stray", "ICE");
        data.entity_element_mappings.put("minecraft:vex", "EVOCATION");
        data.entity_element_mappings.put("minecraft:vindicator", "EVOCATION");
        data.entity_element_mappings.put("minecraft:warden", "ELDRITCH");
        data.entity_element_mappings.put("minecraft:witch", "NATURE");
        data.entity_element_mappings.put("minecraft:wither", "ELDRITCH");
        data.entity_element_mappings.put("minecraft:wither_skeleton", "BLOOD");
        data.entity_element_mappings.put("minecraft:zoglin", "BLOOD");
        data.entity_element_mappings.put("minecraft:zombie", "NATURE");
        data.entity_element_mappings.put("minecraft:zombie_villager", "NATURE");
        data.entity_element_mappings.put("minecraft:zombified_piglin", "BLOOD");
    }

    public static ElementType getElementFor(String entityId) {
        if (!data.enabled) return null;
        String typeStr = data.entity_element_mappings.get(entityId);
        if (typeStr != null) {
            return ElementType.fromString(typeStr);
        }
        return null;
    }
}
