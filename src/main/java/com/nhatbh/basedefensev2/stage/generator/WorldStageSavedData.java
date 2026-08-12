package com.nhatbh.basedefensev2.stage.generator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.nhatbh.basedefensev2.stage.config.StageConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class WorldStageSavedData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "basedefensev2_world_stages";
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, StageConfig>>() {}.getType();

    private final Map<String, StageConfig> stages = new LinkedHashMap<>();
    private long storedSeed = 0L;
    private boolean bossesRegistered = false;

    public WorldStageSavedData() {}

    public static WorldStageSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        WorldStageSavedData data = overworld.getDataStorage().computeIfAbsent(
                WorldStageSavedData::load,
                WorldStageSavedData::new,
                DATA_NAME
        );
        data.ensureInitialized(overworld);
        return data;
    }

    public synchronized void ensureInitialized(ServerLevel overworld) {
        if (stages.isEmpty()) {
            initializeForWorld(overworld.getSeed());
        } else if (!bossesRegistered) {
            reRegisterBosses();
        }
    }

    public static WorldStageSavedData load(CompoundTag nbt) {
        WorldStageSavedData data = new WorldStageSavedData();
        if (nbt.contains("world_seed")) {
            data.storedSeed = nbt.getLong("world_seed");
        }
        if (nbt.contains("stages_json_bytes")) {
            byte[] bytes = nbt.getByteArray("stages_json_bytes");
            String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            try {
                Map<String, StageConfig> loaded = GSON.fromJson(json, MAP_TYPE);
                if (loaded != null) {
                    data.stages.putAll(loaded);
                }
            } catch (Exception e) {
                LOGGER.error("[WorldStageSavedData] Failed to parse stages JSON bytes", e);
            }
        } else if (nbt.contains("stages_json")) {
            String json = nbt.getString("stages_json");
            try {
                Map<String, StageConfig> loaded = GSON.fromJson(json, MAP_TYPE);
                if (loaded != null) {
                    data.stages.putAll(loaded);
                }
            } catch (Exception e) {
                LOGGER.error("[WorldStageSavedData] Failed to parse stages JSON", e);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        nbt.putLong("world_seed", storedSeed);
        try {
            String json = GSON.toJson(stages);
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            nbt.putByteArray("stages_json_bytes", bytes);
        } catch (Exception e) {
            LOGGER.error("[WorldStageSavedData] Failed to serialize stages to JSON", e);
        }
        return nbt;
    }

    public void initializeForWorld(long seed) {
        boolean classificationsUpdated = com.nhatbh.basedefensev2.stage.StageLoader.consumeClassificationsChanged();

        if (stages.isEmpty()) {
            // First-ever generation for this world
            this.storedSeed = seed;
            Map<String, StageConfig> generated = RandomStageGenerator.generateWorldStages(seed);
            stages.putAll(generated);
            bossesRegistered = true;
            setDirty();
        } else if (classificationsUpdated) {
            // mobs.json was reloaded — regenerate stages so new classifications take effect
            LOGGER.info("[WorldStageSavedData] mobs.json changed, regenerating stages with seed {}", storedSeed);
            stages.clear();
            Map<String, StageConfig> generated = RandomStageGenerator.generateWorldStages(storedSeed);
            stages.putAll(generated);
            bossesRegistered = true;
            setDirty();
        }
    }

    /**
     * Called on every server load when stages are already populated.
     * Re-registers dynamic boss definitions into ModBosses using the stored seed
     * so that boss lookups (e.g. gen_boss_stage_1) succeed after a restart.
     */
    public void reRegisterBosses() {
        if (storedSeed != 0L && !stages.isEmpty()) {
            LOGGER.info("[WorldStageSavedData] Re-registering {} dynamic bosses with seed {}", stages.size(), storedSeed);
            RandomStageGenerator.registerBossesOnly(storedSeed);
            bossesRegistered = true;
        }
    }

    /**
     * Unconditionally clears and regenerates the world stage sequence with a new seed.
     * Use this for the /arena reroll command.
     */
    public void clearAndRegenerate(long seed) {
        this.storedSeed = seed;
        stages.clear();
        Map<String, StageConfig> generated = RandomStageGenerator.generateWorldStages(seed);
        stages.putAll(generated);
        setDirty();
    }

    public Collection<StageConfig> getAllStages() {
        return stages.values();
    }

    public Optional<StageConfig> getById(String id) {
        return Optional.ofNullable(stages.get(id));
    }

    public Map<String, StageConfig> getStageMap() {
        return stages;
    }
}
