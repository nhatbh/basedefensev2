package com.nhatbh.basedefensev2.stage.core;

/** Inner micro-states for a single wave while the stage is ACTIVE. */
public enum WaveState {
    /** Fired SpawnRequested event; waiting for SpawnerSubsystem to register UUIDs. */
    SPAWNING,
    /** All enemies are alive. Monitoring UUID set and time limit every tick. */
    COMBAT,
    /** All tracked enemy UUIDs have been removed. Wave is complete. */
    CLEARED,
    /** time_limit_ticks elapsed before enemies were cleared. */
    TIMEOUT,
    /** 5 second delay before next wave starts. */
    WAITING_NEXT_WAVE
}
