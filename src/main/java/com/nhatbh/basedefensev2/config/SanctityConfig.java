package com.nhatbh.basedefensev2.config;

public class SanctityConfig {
    public static ConfigData data = new ConfigData();

    public static class ConfigData {
        /** Maximum amount of sanctity (lives). The world starts at this value. */
        public int maxSanctity = 10;
        /** Maximum amount of grace (Base Energy). */
        public int maxGrace = 200;
        /** Amount of grace regenerated per tick (default 0.01 per tick, or 1 every 100 ticks). */
        public double graceRegenRate = 0.01;
        /** Maximum number of world retries before soft game over (default 999 = unlimited). */
        public int maxWorldRetries = 999;
        /** Duration of intermission countdown in ticks (default 54000 ticks = 45 mins). */
        public int intermissionDurationTicks = 54000;
        /** Percentage of max Sanctity restored upon retrying a stage (default 1.0 = 100% full recovery). */
        public double retrySanctityPercent = 1.0;
        /** Mob health and damage multiplier increase per retry stack (default 0.0 = no penalty). */
        public double retryMobStatMultiplier = 0.0;
        /** Maximum mob health and damage multiplier boost cap from retries (default 0.0 = no penalty). */
        public double maxRetryMobStatBoost = 0.0;
    }

    public static void load() {
        // In-memory defaults only; no external config file dependency
    }

    public static void save() {
        // In-memory defaults only; no external config file dependency
    }
}
