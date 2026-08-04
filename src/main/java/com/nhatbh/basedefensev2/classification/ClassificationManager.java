package com.nhatbh.basedefensev2.classification;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class ClassificationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassificationManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SAVE_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "basedefensev2_mob_classifications.json");

    public static class MobData {
        public String category = "Unclassified";
        public boolean isRanged = false;
        public boolean excluded = false;

        public MobData() {}

        public MobData(String category, boolean isRanged, boolean excluded) {
            this.category = category;
            this.isRanged = isRanged;
            this.excluded = excluded;
        }
    }

    private static Map<String, MobData> classifications = new HashMap<>();

    public static void load() {
        if (!SAVE_FILE.exists()) {
            classifications = new HashMap<>();
            save();
            return;
        }

        try (FileReader reader = new FileReader(SAVE_FILE)) {
            Type type = new TypeToken<Map<String, MobData>>() {}.getType();
            Map<String, MobData> data = GSON.fromJson(reader, type);
            if (data != null) {
                classifications = data;
            } else {
                classifications = new HashMap<>();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load entity classifications JSON", e);
            classifications = new HashMap<>();
        }
    }

    public static void save() {
        try {
            if (!SAVE_FILE.getParentFile().exists()) {
                SAVE_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(SAVE_FILE)) {
                GSON.toJson(classifications, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save entity classifications JSON", e);
        }
    }

    public static MobData getMobData(String mobId) {
        return classifications.getOrDefault(mobId, new MobData("Unclassified", false, false));
    }

    public static String getClassification(String mobId) {
        return getMobData(mobId).category;
    }

    public static boolean isRanged(String mobId) {
        return getMobData(mobId).isRanged;
    }

    public static boolean isExcluded(String mobId) {
        return getMobData(mobId).excluded;
    }

    public static void setClassification(String mobId, String category, boolean isRanged, boolean excluded) {
        classifications.put(mobId, new MobData(category, isRanged, excluded));
        save();
    }

    public static void setExcluded(String mobId, boolean excluded) {
        MobData data = getMobData(mobId);
        data.excluded = excluded;
        classifications.put(mobId, data);
        save();
    }

    public static Map<String, MobData> getClassifications() {
        return classifications;
    }

    /**
     * Replaces the in-memory classifications with data loaded from mobs.json.
     * Called by StageLoader on every resource reload so that mobs.json is always
     * the canonical source of truth.
     */
    public static void loadFromMap(Map<String, MobData> data) {
        classifications = new HashMap<>(data);
        save(); // Keep the config-dir file in sync for the classification screen
    }
}
