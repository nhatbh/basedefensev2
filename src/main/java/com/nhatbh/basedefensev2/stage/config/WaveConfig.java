package com.nhatbh.basedefensev2.stage.config;

import java.util.Collections;
import java.util.List;

/**
 * Configuration for a single combat wave, loaded from JSON.
 *
 * Formation:
 *   A single random direction is chosen for the wave. All entries with
 *   formation="arc" are scattered within ±(arc_angle/2) of that direction.
 *   Use distance_min/distance_max on each entry to place them in rows
 *   (small distance = front/tank row, large distance = back/ranged row).
 */
public class WaveConfig {
    /** Unique identifier for this wave (used in logs/events) */
    public String id;
    /** Maximum ticks before TIMEOUT if enemies are not cleared. 0 = unlimited */
    public int time_limit_ticks = 0;
    /**
     * Total angular spread in degrees for arc-formation mobs in this wave.
     * Each mob is placed at a random angle within ±(arc_angle/2) of the
     * shared wave direction. Default 90° = wide fan.
     */
    public float arc_angle = 90.0f;
    /** Ordered list of mob/boss entries to spawn */
    public List<MobSpawnEntry> mobs = Collections.emptyList();
    /** Reward config only applied on the FINAL wave when entering SCAVENGE */
    public WaveRewardConfig rewards = new WaveRewardConfig();
}
