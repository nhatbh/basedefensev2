package com.nhatbh.basedefensev2.stage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.stage.config.StageConfig;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads all stage JSON configs from data/basedefensev2/stages/ on server
 * resource reload.  Registered via AddReloadListenerEvent in BaseDefenseMod.
 */
public class StageLoader extends SimplePreparableReloadListener<Map<String, StageConfig>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String FOLDER = "stages";

    /** Singleton for event-bus registration */
    public static final StageLoader INSTANCE = new StageLoader();

    /** Loaded configs keyed by stage id */
    private static final Map<String, StageConfig> STAGES = new LinkedHashMap<>();

    private StageLoader() {}

    // ── SimplePreparableReloadListener ──────────────────────────────────────

    @Override
    protected Map<String, StageConfig> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, StageConfig> loaded = new LinkedHashMap<>();

        manager.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"))
                .forEach((location, resource) -> {
                    if (!location.getNamespace().equals(BaseDefenseMod.MODID)) return;
                    try (InputStreamReader reader = new InputStreamReader(
                            resource.open(), StandardCharsets.UTF_8)) {
                        StageConfig cfg = GSON.fromJson(reader, StageConfig.class);
                        if (cfg == null || cfg.id == null) {
                            LOGGER.warn("[StageLoader] Skipping {}: missing 'id' field", location);
                            return;
                        }
                        loaded.put(cfg.id, cfg);
                        LOGGER.info("[StageLoader] Loaded stage '{}' from {}", cfg.id, location);
                    } catch (Exception e) {
                        LOGGER.error("[StageLoader] Failed to load {}: {}", location, e.getMessage());
                    }
                });

        return loaded;
    }

    @Override
    protected void apply(Map<String, StageConfig> prepared, ResourceManager manager, ProfilerFiller profiler) {
        STAGES.clear();
        STAGES.putAll(prepared);
        LOGGER.info("[StageLoader] Registered {} stage(s)", STAGES.size());
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** All loaded stage configs in insertion order (i.e. definition order). */
    public static Collection<StageConfig> getAllStages() {
        return Collections.unmodifiableCollection(STAGES.values());
    }

    public static Optional<StageConfig> getById(String id) {
        return Optional.ofNullable(STAGES.get(id));
    }
}
