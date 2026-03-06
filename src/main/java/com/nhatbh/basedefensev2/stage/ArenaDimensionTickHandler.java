package com.nhatbh.basedefensev2.stage;

import com.nhatbh.basedefensev2.stage.core.StageContext;
import com.nhatbh.basedefensev2.stage.core.WaveState;
import com.nhatbh.basedefensev2.stage.network.StageHudSyncPacket;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Drives the StageContext tick and HUD sync via {@link TickEvent.ServerTickEvent}.
 *
 * Using ServerTickEvent (rather than LevelTickEvent) is intentional:
 *  - The stage state machine must advance even when no players are inside the arena
 *    dimension. Minecraft may stop ticking a dimension when it has no players, which
 *    would freeze the state machine if we relied on LevelTickEvent.
 *  - The arena ServerLevel is always accessible via server.getLevel(ARENA) as long as
 *    the server is running, so we can drive StageContext.tick() from here safely.
 *
 * Every tick  → StageContext.tick()  (state machine)
 * Every 10 t  → StageHudSyncPacket  → ALL connected players
 */
public class ArenaDimensionTickHandler {

    private int syncCounter = 0;

    // ── Server tick ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel arenaLevel = server.getLevel(ModDimensions.ARENA);
        if (arenaLevel == null) return;

        StageContext ctx = StageContext.getOrCreate(arenaLevel);

        // 1. Advance the stage state machine every tick, regardless of player location
        ctx.tick(arenaLevel);

        // 2. Process pending teleports (stand-still logic)
        com.nhatbh.basedefensev2.stage.TeleportManager.tick();

        // 3. Broadcast HUD snapshot every 10 ticks to ALL connected players
        syncCounter++;
        if (syncCounter % 10 == 0) {
            StageHudSyncPacket pkt = buildPacket(ctx, arenaLevel);
            NetworkManager.INSTANCE.send(PacketDistributor.ALL.noArg(), pkt);
        }
    }

    // ── Death listener (immediate enemy removal from tracked set) ────────────

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(ModDimensions.ARENA)) return;

        StageContext ctx = StageContext.getOrCreate(level);
        if (ctx.isActive()) {
            ctx.onEntityDied(event.getEntity().getUUID());
        }
    }

    // ── Packet builder ───────────────────────────────────────────────────────

    private static StageHudSyncPacket buildPacket(StageContext ctx, ServerLevel level) {
        if (!ctx.isActive()) {
            return new StageHudSyncPacket(false, "", "", 0, 0, 0, 0, 0, 0, -1f, 0, ctx.getTicksUntilNextStage(level));
        }

        String stageState = ctx.getStageState() != null ? ctx.getStageState().name() : "";
        String waveState  = ctx.getWaveState()  != null ? ctx.getWaveState().name()  : "";

        float timerRatio = -1f;
        int waveRemTicks = 0;
        int stageRemTicks = 0;

        if (ctx.getStageState() == com.nhatbh.basedefensev2.stage.core.StageState.WARMUP) {
            stageRemTicks = Math.max(0, ctx.getActiveConfig().warmup_ticks - ctx.getStageTicks());
        } else if (ctx.getStageState() == com.nhatbh.basedefensev2.stage.core.StageState.SCAVENGE) {
            stageRemTicks = Math.max(0, ctx.getActiveConfig().scavenge_duration_ticks - ctx.getStageTicks());
        }

        if (ctx.getActiveConfig() != null) {
            if (ctx.getWaveState() == WaveState.COMBAT) {
                int timeLimit = ctx.getActiveConfig().waves.get(ctx.getCurrentWaveIndex()).time_limit_ticks;
                if (timeLimit > 0) {
                    waveRemTicks = Math.max(0, timeLimit - ctx.getWaveTicks());
                    timerRatio = (float) waveRemTicks / timeLimit;
                }
            } else if (ctx.getWaveState() == WaveState.WAITING_NEXT_WAVE) {
                waveRemTicks = Math.max(0, 100 - ctx.getWaveTicks());
                timerRatio = (float) waveRemTicks / 100f;
            }
        }
        timerRatio = Math.max(0f, Math.min(1f, timerRatio));

        return new StageHudSyncPacket(
                true,
                stageState,
                waveState,
                ctx.getCurrentWaveIndex(),
                ctx.getActiveConfig().waves.size(),
                ctx.getLivingEnemyCount(),
                ctx.getTotalEnemiesInWave(),
                ctx.getStageTicks(),
                stageRemTicks,
                timerRatio,
                waveRemTicks,
                0 // idleTicks is 0 when stage is active
        );
    }
}
