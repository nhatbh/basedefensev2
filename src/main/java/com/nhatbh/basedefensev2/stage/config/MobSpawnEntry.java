package com.nhatbh.basedefensev2.stage.config;

/**
 * Represents a single spawn entry inside a WaveConfig.
 * Gson-deserializable from JSON.
 *
 * Formation system  (formation = "arc"):
 *   All entries in the wave share a single random direction and a single
 *   arc_angle (defined on the WaveConfig). This entry's distance_min/max
 *   controls which radial row the mobs occupy:
 *     - Small distance → front row (melee/tanks, rush first)
 *     - Large distance → back row (ranged, shielded by front row)
 *
 * Example:
 *   { "type": "zombie",   "count": 6, "formation": "arc", "distance_min":  5, "distance_max": 12 }
 *   { "type": "skeleton", "count": 3, "formation": "arc", "distance_min": 15, "distance_max": 22 }
 *   → Zombies up front, skeletons in the rear, all facing the same direction.
 */
public class MobSpawnEntry {
    // ── Core ──────────────────────────────────────────────────────────────────
    /** The entity type registry name, e.g. "minecraft:zombie" */
    public String type;
    /** Number of this entity to spawn */
    public int count = 1;
    /** True if this entry uses a boss_id from the BossRegistry */
    public boolean is_boss = false;
    /** Boss definition ID (used only when is_boss = true) */
    public String boss_id;

    // ── Formation ─────────────────────────────────────────────────────────────
    /**
     * "random" (default) — scatter uniformly within spawn_area radius.
     * "arc"              — place within the wave's shared arc cone.
     *                      Uses distance_min/max to determine the row.
     */
    public String formation = "random";

    /** Minimum distance from spawn origin along the arc direction. */
    public float distance_min = 5.0f;
    /** Maximum distance from spawn origin along the arc direction. */
    public float distance_max = 15.0f;

    /** Optional custom loot drops for this entry. */
    public java.util.List<LootEntry> loot = new java.util.ArrayList<>();

    public static class LootEntry {
        public String item;
        public int min = 1;
        public int max = 1;
        public int weight = 1;
    }
}
