package com.nhatbh.basedefensev2.stage.config;

import java.util.Collections;
import java.util.List;

/**
 * Top-level configuration for an arena stage, loaded from
 * data/basedefensev2/stages/<id>.json
 */
public class StageConfig {
    /** Unique stage identifier, must match filename */
    public String id;
    /**
     * Seconds to wait after the last stage ended (or since world creation for
     * the first stage) before triggering this stage.
     */
    public long trigger_seconds = 3600;
    /** Order of the stage. Stages with the same order are selected randomly. */
    public int order = 0;
    /** Ticks of warmup countdown before wave combat begins */
    public int warmup_ticks = 6000;
    /** Setup radius around the barrier center where wave mobs can spawn */
    public double spawn_radius = 25;
    /** Ticks of the post-victory scavenge (loot collection) window */
    public int scavenge_duration_ticks = 6000;
    /** Optional explicit elemental override for this stage (e.g., "FIRE", "ICE") */
    public String element;
    /** Ordered list of waves */
    public List<WaveConfig> waves = Collections.emptyList();

    /**
     * Resolves the primary elemental affiliation of this stage.
     * Uses explicit `element` string if set, otherwise infers from wave mobs,
     * defaulting to FIRE if unspecified.
     */
    public com.nhatbh.basedefensev2.elemental.ElementType getElement() {
        if (element != null && !element.isEmpty()) {
            com.nhatbh.basedefensev2.elemental.ElementType parsed = com.nhatbh.basedefensev2.elemental.ElementType.fromString(element);
            if (parsed != null) return parsed;
        }

        if (waves != null) {
            for (WaveConfig w : waves) {
                if (w.mobs != null) {
                    for (MobSpawnEntry entry : w.mobs) {
                        if (entry.type != null) {
                            com.nhatbh.basedefensev2.elemental.ElementType elem = com.nhatbh.basedefensev2.elemental.MobElementConfig.getElementFor(entry.type);
                            if (elem != null) {
                                return elem;
                            }
                        }
                    }
                }
            }
        }

        return com.nhatbh.basedefensev2.elemental.ElementType.FIRE;
    }

    public static class SpawnArea {
        public double x = 0;
        public double y = 64;
        public double z = 0;
        public double radius = 25;
    }
}
