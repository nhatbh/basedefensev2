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
    /** Ordered list of waves */
    public List<WaveConfig> waves = Collections.emptyList();

    public static class SpawnArea {
        public double x = 0;
        public double y = 64;
        public double z = 0;
        public double radius = 25;
    }
}
