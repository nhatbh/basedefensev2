package com.nhatbh.basedefensev2.stage.config;

import java.util.Collections;
import java.util.List;

/**
 * Reward granted at the start of the SCAVENGE phase (victory).
 * Gson-deserializable from JSON.
 */
public class WaveRewardConfig {
    /** Flat XP points granted to all players in the arena */
    public int xp = 0;
    /** List of console commands to run (use @a/@p for targeting) */
    public List<String> commands = Collections.emptyList();
    /** List of items to drop at the arena centre */
    public List<ItemDropEntry> items = Collections.emptyList();

    public static class ItemDropEntry {
        /** Item registry name, e.g. "minecraft:diamond" */
        public String item;
        public int count = 1;
    }
}
