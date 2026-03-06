package com.nhatbh.basedefensev2.stage.events;

import com.nhatbh.basedefensev2.stage.config.WaveConfig;
import com.nhatbh.basedefensev2.stage.core.StageContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

/**
 * Forge event bus events for the stage/wave lifecycle.
 * All fired on MinecraftForge.EVENT_BUS (server-side only).
 */
public class WaveEvents {

    /**
     * Fired when a new wave begins and needs entities spawned.
     * The Spawner subsystem handles this, spawns entities, then calls
     * {@link StageContext#registerEnemies} and transitions to COMBAT.
     */
    public static class SpawnRequested extends Event {
        private final WaveConfig wave;
        private final StageContext ctx;
        private final ServerLevel level;

        public SpawnRequested(WaveConfig wave, StageContext ctx, ServerLevel level) {
            this.wave = wave;
            this.ctx = ctx;
            this.level = level;
        }

        public WaveConfig getWave() { return wave; }
        public StageContext getCtx() { return ctx; }
        public ServerLevel getLevel() { return level; }
    }

    /**
     * Fired on the first tick of the SCAVENGE phase.
     * The Reward subsystem grants XP, runs commands, and drops items.
     */
    public static class LootPhaseStarted extends Event {
        private final WaveConfig finalWave;
        private final ServerLevel level;
        private final List<ServerPlayer> players;

        public LootPhaseStarted(WaveConfig finalWave, ServerLevel level, List<ServerPlayer> players) {
            this.finalWave = finalWave;
            this.level = level;
            this.players = players;
        }

        public WaveConfig getFinalWave() { return finalWave; }
        public ServerLevel getLevel() { return level; }
        public List<ServerPlayer> getPlayers() { return players; }
    }

    /**
     * Fired when the ENDED state is reached.
     * The Cleanup subsystem wipes entities and teleports players out.
     */
    public static class StageEnded extends Event {
        private final ServerLevel level;
        private final AABB arenaBounds;

        public StageEnded(ServerLevel level, AABB arenaBounds) {
            this.level = level;
            this.arenaBounds = arenaBounds;
        }

        public ServerLevel getLevel() { return level; }
        public AABB getArenaBounds() { return arenaBounds; }
    }
}
