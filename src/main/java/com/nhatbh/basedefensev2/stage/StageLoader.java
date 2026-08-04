package com.nhatbh.basedefensev2.stage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.classification.ClassificationManager;
import com.nhatbh.basedefensev2.stage.config.StageConfig;
import com.nhatbh.basedefensev2.stage.generator.WorldStageSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Stage configuration access layer. All stage configs are generated procedurally
 * per-world via {@link com.nhatbh.basedefensev2.stage.generator.RandomStageGenerator}
 * and persisted in {@link WorldStageSavedData}.
 *
 * <p>On every resource reload this class also reloads {@code mobs.json} and
 * pushes its contents into {@link ClassificationManager} so that stage generation
 * always reflects the current classification data.  When classifications change
 * the persisted world stage data is invalidated so the next world load triggers
 * a fresh generation pass.</p>
 */
public class StageLoader extends SimplePreparableReloadListener<Map<String, ClassificationManager.MobData>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String MOBS_FILE = "stages/mobs.json";
    private static final Type MOB_DATA_TYPE =
            new TypeToken<Map<String, ClassificationManager.MobData>>() {}.getType();

    /** Set to true by apply() when mobs.json changed; consumed by WorldStageSavedData on next world load. */
    private static volatile boolean classificationsChanged = false;

    /** Singleton for event-bus registration */
    public static final StageLoader INSTANCE = new StageLoader();

    private StageLoader() {}

    // ── SimplePreparableReloadListener ───────────────────────────────────────

    @Override
    protected Map<String, ClassificationManager.MobData> prepare(ResourceManager manager, ProfilerFiller profiler) {
        var resourceOpt = manager.getResource(
                new net.minecraft.resources.ResourceLocation(BaseDefenseMod.MODID, MOBS_FILE));
        if (resourceOpt.isEmpty()) {
            LOGGER.warn("[StageLoader] mobs.json not found at data/{}/{}", BaseDefenseMod.MODID, MOBS_FILE);
            return Collections.emptyMap();
        }
        try (InputStreamReader reader = new InputStreamReader(
                resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            Map<String, ClassificationManager.MobData> data = GSON.fromJson(reader, MOB_DATA_TYPE);
            return data != null ? data : Collections.emptyMap();
        } catch (Exception e) {
            LOGGER.error("[StageLoader] Failed to load mobs.json", e);
            return Collections.emptyMap();
        }
    }

    @Override
    protected void apply(Map<String, ClassificationManager.MobData> prepared,
                         ResourceManager manager, ProfilerFiller profiler) {
        if (prepared.isEmpty()) {
            LOGGER.warn("[StageLoader] mobs.json was empty or failed to parse; classifications unchanged.");
            return;
        }
        ClassificationManager.loadFromMap(prepared);
        classificationsChanged = true;
        LOGGER.info("[StageLoader] Loaded {} mob classifications from mobs.json", prepared.size());
    }

    // ── Stage invalidation API (called by WorldStageSavedData on init) ───────

    /**
     * Returns true if mobs.json was reloaded since the last call to this method.
     * Calling this method resets the flag.
     */
    public static boolean consumeClassificationsChanged() {
        boolean changed = classificationsChanged;
        classificationsChanged = false;
        return changed;
    }

    // ── Public API — all backed by WorldStageSavedData ───────────────────────

    /** All stage configs for this world (randomized, persisted). */
    public static Collection<StageConfig> getAllStages(ServerLevel level) {
        return WorldStageSavedData.get(level).getAllStages();
    }

    /** Look up a stage by id in this world's persisted stage map. */
    public static Optional<StageConfig> getById(ServerLevel level, String id) {
        return WorldStageSavedData.get(level).getById(id);
    }

    /** Returns all unique order values in ascending order for this world. */
    public static List<Integer> getSortedOrders(ServerLevel level) {
        return getAllStages(level).stream()
                .map(s -> s.order)
                .distinct()
                .sorted()
                .toList();
    }

    /** Returns all stages matching a specific order for this world. */
    public static List<StageConfig> getStagesForOrder(ServerLevel level, int order) {
        return getAllStages(level).stream()
                .filter(s -> s.order == order)
                .toList();
    }
}
