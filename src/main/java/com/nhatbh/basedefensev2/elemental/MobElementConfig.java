package com.nhatbh.basedefensev2.elemental;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MobElementConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ConfigData data = new ConfigData();

    public static class ConfigData {
        public boolean enabled = true;
        public Map<String, String> entity_element_mappings = new HashMap<>();
    }

    public static void load() {
        try (InputStream stream = MobElementConfig.class.getResourceAsStream("/data/basedefensev2/elements.json")) {
            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    data = GSON.fromJson(reader, ConfigData.class);
                    if (data != null && data.entity_element_mappings != null) {
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load elements.json from data resource", e);
        }

        data = new ConfigData();
    }

    public static ElementType getElementFor(String entityId) {
        if (!data.enabled)
            return null;
        String typeStr = data.entity_element_mappings.get(entityId);
        if (typeStr != null) {
            return ElementType.fromString(typeStr);
        }
        return null;
    }
}
