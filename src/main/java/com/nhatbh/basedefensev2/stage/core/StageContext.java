package com.nhatbh.basedefensev2.stage.core;

import com.mojang.logging.LogUtils;
import com.nhatbh.basedefensev2.stage.StageLoader;
import com.nhatbh.basedefensev2.stage.config.StageConfig;
import com.nhatbh.basedefensev2.stage.config.WaveConfig;
import com.nhatbh.basedefensev2.stage.events.WaveEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

import java.util.*;

/**
 * The "Game Master" object for an arena stage.
 *
 * Persisted as SavedData so it survives server restarts. Holds all mutable
 * state: the active stage config, the outer StageState, the inner WaveState,
 * tick counters, the current wave index, and the set of living enemy UUIDs.
 *
 * Outer tick loop (driven by ArenaDimensionTickHandler every server tick):
 *   WARMUP  → ACTIVE  → SCAVENGE  → ENDED
 *
 * Inner wave loop (delegated from ACTIVE):
 *   SPAWNING → COMBAT → CLEARED (→ next wave or SCAVENGE)
 *                     → TIMEOUT  (optional failure path)
 *
 * All side effects (spawning, rewards, cleanup) are handled by subsystems
 * listening to WaveEvents on MinecraftForge.EVENT_BUS.
 *
 * Also tracks the inter-stage timer:
 *   latestStageEndGameTime = 0 means "world just created, no stage has ended yet".
 *   When (gameTime - latestStageEndGameTime) >= trigger_seconds * 20, the next
 *   pending stage can begin.
 */
public class StageContext extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Running tick counter used purely for debug broadcasts (resets at Long.MAX_VALUE) */
    private long totalTicks = 0;
    private static final String SAVE_KEY = "basedefensev2_arena";

    // ── Inter-stage progression ──────────────────────────────────────────────
    /** Game-time tick when the last stage ended (0 = world creation). */
    private long lastStageEndGameTime = 0;
    /** Index into StageLoader.getAllStages() list for the next stage to run */
    private int nextStageIndex = 0;

    // ── Active stage state ───────────────────────────────────────────────────
    /** Null when no stage is currently active */
    private StageConfig activeConfig = null;

    private StageState stageState = null;
    private WaveState waveState = null;

    /** Ticks elapsed in the current stage phase (warmup / scavenge timers) */
    private int stageTicks = 0;
    /** Ticks elapsed in the current wave (for time_limit checking) */
    private int waveTicks = 0;
    /** Index into activeConfig.waves */
    private int currentWaveIndex = 0;

    /** UUIDs of all enemies spawned for the current wave that are still alive */
    private final Set<UUID> livingEnemies = new HashSet<>();

    /** Total enemies spawned at the start of the current wave (set once in registerEnemies, reset on new wave) */
    private int totalEnemiesInWave = 0;

    /** True on the first tick of SCAVENGE so rewards fire exactly once */
    private boolean scavengeRewardFired = false;

    // ── SavedData factory ────────────────────────────────────────────────────

    public static StageContext getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                StageContext::load,
                StageContext::new,
                SAVE_KEY
        );
    }

    // ── Main tick entry point (called by ArenaDimensionTickHandler) ──────────

    /**
     * Called every server tick while the level is the arena dimension.
     */
    public void tick(ServerLevel level) {
        totalTicks++;
        boolean debugSecond = (totalTicks % 20 == 0);

        if (activeConfig == null) {
            tryTriggerNextStage(level);
            if (debugSecond) debugTickIdle(level);
            return;
        }

        switch (stageState) {
            case WARMUP -> tickWarmup(level);
            case ACTIVE -> tickActive(level);
            case SCAVENGE -> tickScavenge(level);
            case ENDED -> {} // No-op; ArenaDimensionTickHandler will see activeConfig == null next cycle
        }

        if (debugSecond) debugTickActive(level);
        setDirty();
    }

    // ── Trigger check ────────────────────────────────────────────────────────

    private void tryTriggerNextStage(ServerLevel level) {
        List<StageConfig> stages = new ArrayList<>(StageLoader.getAllStages());
        if (nextStageIndex >= stages.size()) return; // All stages done

        StageConfig candidate = stages.get(nextStageIndex);
        long elapsed = level.getGameTime() - lastStageEndGameTime;
        long required = candidate.trigger_seconds * 20L;

        if (elapsed < required) return;

        // Trigger!
        LOGGER.info("[StageContext] Triggering stage '{}' (elapsed={} ticks)", candidate.id, elapsed);
        activeConfig = candidate;
        stageState = StageState.WARMUP;
        waveState = null;
        stageTicks = 0;
        waveTicks = 0;
        currentWaveIndex = 0;
        livingEnemies.clear();
        scavengeRewardFired = false;

        broadcastToArena(level, "§6[Arena] §eA new stage is beginning! Prepare yourself...");
        
        // Interactive JOIN message
        Component joinMsg = Component.literal("§6[Arena] §eA new stage has started! ")
                .append(Component.literal("§l[CLICK HERE TO JOIN]")
                        .withStyle(style -> style
                                .withColor(net.minecraft.ChatFormatting.GOLD)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/arena join"))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to join the arena!")))
                        ));
        level.getServer().getPlayerList().broadcastSystemMessage(joinMsg, false);
        
        setDirty();
    }

    // ── WARMUP ───────────────────────────────────────────────────────────────

    private void tickWarmup(ServerLevel level) {
        stageTicks++;
        int remaining = activeConfig.warmup_ticks - stageTicks;

        // Countdown broadcasts at round intervals
        if (remaining == 100 || remaining == 60 || remaining == 40 ||
                remaining == 20 || remaining == 10 || remaining == 5 ||
                remaining == 4 || remaining == 3 || remaining == 2 || remaining == 1) {
            broadcastToArena(level, "§e[Arena] Stage begins in §c" + (remaining / 20) + "s§e...");
        }

        if (stageTicks >= activeConfig.warmup_ticks) {
            LOGGER.info("[StageContext] Stage '{}' WARMUP complete → ACTIVE", activeConfig.id);
            stageState = StageState.ACTIVE;
            stageTicks = 0;
            startNextWave(level);
        }

        // Forced teleport logic (if arena is empty)
        if (level.players().isEmpty()) {
            if (remaining == 4800) { // 4 mins rem (3 mins until force)
                broadcastToServer(level, "§6[Arena] §eWarning: All players will be forcefully teleported in §c3 minutes§e if no one enters the arena!");
            } else if (remaining == 3600) { // 3 mins rem (2 mins until force)
                broadcastToServer(level, "§6[Arena] §eWarning: All players will be forcefully teleported in §c2 minutes§e if no one enters the arena!");
            } else if (remaining == 2400) { // 2 mins rem (1 min until force)
                broadcastToServer(level, "§6[Arena] §eWarning: All players will be forcefully teleported in §c1 minute§e if no one enters the arena!");
            } else if (remaining == 1300) { // 5s before force
                broadcastToServer(level, "§c[Arena] Warning: Forced teleportation in 5 seconds!");
            } else if (remaining == 1200) { // 1 min rem (ACTUAL FORCE)
                broadcastToServer(level, "§c[Arena] No players in the arena! Commencing forced extraction...");
                com.nhatbh.basedefensev2.stage.TeleportManager.forceTeleportAll(level);
            }
        }
    }

    private void broadcastToServer(ServerLevel level, String message) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    // ── ACTIVE (outer) ───────────────────────────────────────────────────────

    private void tickActive(ServerLevel level) {
        if (waveState == null) return;

        switch (waveState) {
            case SPAWNING -> {
                // SpawnRequested was already fired; wait for SpawnerSubsystem
                // to call registerEnemies() which flips state to COMBAT.
                waveTicks++;
                // Safety: if no enemies registered within 20 ticks, auto-clear
                if (waveTicks > 20 && livingEnemies.isEmpty()) {
                    LOGGER.warn("[StageContext] No enemies registered after 20 ticks; auto-advancing.");
                    waveState = WaveState.CLEARED;
                    tickWaveCleared(level);
                }
            }
            case COMBAT -> tickCombat(level);
            case CLEARED -> tickWaveCleared(level);
            case TIMEOUT -> tickWaveTimeout(level);
            case WAITING_NEXT_WAVE -> tickWaitingNextWave(level);
        }
    }

    private void tickWaitingNextWave(ServerLevel level) {
        waveTicks++;
        int remaining = 100 - waveTicks; // 5 seconds = 100 ticks

        if (remaining % 20 == 0 && remaining > 0) {
            broadcastToArena(level, "§e[Arena] Next wave in §c" + (remaining / 20) + "s§e...");
        }

        if (waveTicks >= 100) {
            startNextWave(level);
        }
    }

    private void tickCombat(ServerLevel level) {
        waveTicks++;

        // Prune dead / removed entities from the living set
        WaveConfig wave = currentWave();
        livingEnemies.removeIf(uuid -> level.getEntity(uuid) == null);

        // Win condition
        if (livingEnemies.isEmpty()) {
            LOGGER.info("[StageContext] Wave '{}' — all enemies defeated → CLEARED", wave.id);
            broadcastToArena(level, "§a[Arena] Wave cleared!");
            waveState = WaveState.CLEARED;
            tickWaveCleared(level);
            return;
        }

        // Lose condition
        if (wave.time_limit_ticks > 0 && waveTicks >= wave.time_limit_ticks) {
            LOGGER.info("[StageContext] Wave '{}' — time limit reached → TIMEOUT", wave.id);
            broadcastToArena(level, "§c[Arena] Time's up! The wave could not be cleared.");
            waveState = WaveState.TIMEOUT;
            tickWaveTimeout(level);
        }
    }

    private void tickWaveCleared(ServerLevel level) {
        if (currentWaveIndex + 1 < activeConfig.waves.size()) {
            // More waves remain -> WAIT
            LOGGER.info("[StageContext] Wave '{}' cleared -> WAITING_NEXT_WAVE", currentWave().id);
            currentWaveIndex++;
            waveState = WaveState.WAITING_NEXT_WAVE;
            waveTicks = 0;
        } else {
            // Final wave cleared → SCAVENGE
            currentWaveIndex++;
            LOGGER.info("[StageContext] All waves cleared → SCAVENGE");
            stageState = StageState.SCAVENGE;
            stageTicks = 0;
            scavengeRewardFired = false;
        }
    }

    private void tickWaveTimeout(ServerLevel level) {
        if (currentWaveIndex + 1 < activeConfig.waves.size()) {
            LOGGER.info("[StageContext] Wave '{}' timeout -> WAITING_NEXT_WAVE", currentWave().id);
            currentWaveIndex++;
            waveState = WaveState.WAITING_NEXT_WAVE;
            waveTicks = 0;
        } else {
            currentWaveIndex++;
            stageState = StageState.SCAVENGE;
            stageTicks = 0;
            scavengeRewardFired = false;
        }
    }

    private void startNextWave(ServerLevel level) {
        WaveConfig wave = currentWave();
        waveState = WaveState.SPAWNING;
        waveTicks = 0;
        livingEnemies.clear();
        totalEnemiesInWave = 0;

        broadcastToArena(level, "§6[Arena] §eWave §c" + (currentWaveIndex + 1)
                + "§e / §c" + activeConfig.waves.size() + " §ebeginning!");

        LOGGER.info("[StageContext] Firing SpawnRequested for wave '{}'", wave.id);
        MinecraftForge.EVENT_BUS.post(new WaveEvents.SpawnRequested(wave, this, level));
    }

    // ── SCAVENGE ─────────────────────────────────────────────────────────────

    private void tickScavenge(ServerLevel level) {
        // Tick 1: fire reward event exactly once
        if (!scavengeRewardFired) {
            scavengeRewardFired = true;
            WaveConfig finalWave = activeConfig.waves.get(activeConfig.waves.size() - 1);
            List<ServerPlayer> players = new ArrayList<>(level.players());
            broadcastToArena(level, "§a[Arena] §lVICTORY! §rCollect your loot. Arena closes in §e"
                    + (activeConfig.scavenge_duration_ticks / 20) + "s§r.");
            
            // Interactive LEAVE message
            Component leaveMsg = Component.literal("§a[Arena] §eVictory! Collect your loot. ")
                    .append(Component.literal("§l[CLICK TO LEAVE]")
                            .withStyle(style -> style
                                    .withColor(net.minecraft.ChatFormatting.GREEN)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/arena leave"))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to leave the arena.")))
                            ));
            level.getServer().getPlayerList().broadcastSystemMessage(leaveMsg, false);
            
            MinecraftForge.EVENT_BUS.post(new WaveEvents.LootPhaseStarted(finalWave, level, players));
        }

        stageTicks++;

        // Countdown at 60 s, 30 s, 10 s, 5 s
        int remaining = activeConfig.scavenge_duration_ticks - stageTicks;
        if (remaining == 1200 || remaining == 600 || remaining == 200 ||
                remaining == 100) {
            broadcastToArena(level, "§e[Arena] Arena closes in §c" + (remaining / 20) + "s§e.");
        }

        if (stageTicks >= activeConfig.scavenge_duration_ticks) {
            LOGGER.info("[StageContext] Scavenge phase over → ENDED");
            enterEnded(level);
        }
    }

    // ── ENDED ────────────────────────────────────────────────────────────────

    private void enterEnded(ServerLevel level) {
        stageState = StageState.ENDED;
        lastStageEndGameTime = level.getGameTime();
        nextStageIndex++;

        AABB bounds = buildArenaBounds();
        MinecraftForge.EVENT_BUS.post(new WaveEvents.StageEnded(level, bounds));

        broadcastToArena(level, "§7[Arena] The arena has been reset.");

        // Null out active config so the context is idle
        activeConfig = null;
        stageState = null;
        waveState = null;
        setDirty();
    }

    // ── Public API for subsystems ────────────────────────────────────────────

    /**
     * Called by SpawnerSubsystem after all entities for the current wave have
     * been spawned. Transitions wave state from SPAWNING to COMBAT.
     */
    public void registerEnemies(Collection<UUID> uuids) {
        livingEnemies.addAll(uuids);
        totalEnemiesInWave += uuids.size();
        if (waveState == WaveState.SPAWNING) {
            waveState = WaveState.COMBAT;
            LOGGER.info("[StageContext] Registered {} enemies (total={}); moving to COMBAT", uuids.size(), totalEnemiesInWave);
        }
        setDirty();
    }

    /**
     * Called by LivingDeathEvent listener when an entity dies in the arena.
     * Pruning also happens passively in tickCombat().
     */
    public void onEntityDied(UUID uuid) {
        livingEnemies.remove(uuid);
        setDirty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private WaveConfig currentWave() {
        return activeConfig.waves.get(currentWaveIndex);
    }

    private AABB buildArenaBounds() {
        if (activeConfig == null) return new AABB(-50, -64, -50, 50, 320, 50);
        StageConfig.SpawnArea area = activeConfig.spawn_area;
        double r = area.radius + 10; // extra margin for cleanup
        return new AABB(area.x - r, 0, area.z - r, area.x + r, 256, area.z + r);
    }

    private void broadcastToArena(ServerLevel level, String message) {
        level.players().forEach(p -> p.sendSystemMessage(Component.literal(message)));
    }

    // ── Debug broadcasts (every second) ─────────────────────────────────────

    private void debugTickIdle(ServerLevel level) {
        if (level.players().isEmpty()) return;

        List<StageConfig> stages = new ArrayList<>(StageLoader.getAllStages());
        if (nextStageIndex >= stages.size()) {
            broadcastToArena(level, "§8[DBG] §7All stages complete. No more stages.");
            return;
        }

        StageConfig next = stages.get(nextStageIndex);
        long elapsed   = level.getGameTime() - lastStageEndGameTime;
        long required  = next.trigger_seconds * 20L;
        long remaining = Math.max(0, required - elapsed);
        long remSec    = remaining / 20;

        broadcastToArena(level,
            String.format("§8[DBG] §7IDLE | Next: §e%s §7| Trigger in §c%ds §7(elapsed §a%ds §7/ §a%ds§7)",
                next.id, remSec, elapsed / 20, required / 20));
    }

    private void debugTickActive(ServerLevel level) {
        if (level.players().isEmpty()) return;
        if (activeConfig == null || stageState == null) return;

        String msg;
        switch (stageState) {
            case WARMUP -> {
                int rem = activeConfig.warmup_ticks - stageTicks;
                msg = String.format("§8[DBG] §bWARMUP §7| Stage: §e%s §7| Starts in §c%.1fs",
                    activeConfig.id, rem / 20.0f);
            }
            case ACTIVE -> {
                WaveConfig wave = currentWave();
                String waveInfo;
                if (waveState == WaveState.COMBAT) {
                    int timeLimitSec = wave.time_limit_ticks > 0 ? (wave.time_limit_ticks - waveTicks) / 20 : -1;
                    waveInfo = String.format("COMBAT | Enemies: §c%d §7| Wave time: §e%s",
                        livingEnemies.size(),
                        timeLimitSec >= 0 ? timeLimitSec + "s" : "unlimited");
                } else {
                    waveInfo = "" + waveState;
                }
                msg = String.format("§8[DBG] §aACTIVE §7| Stage: §e%s §7| Wave §c%d§7/§c%d §7| %s",
                    activeConfig.id, currentWaveIndex + 1, activeConfig.waves.size(), waveInfo);
            }
            case SCAVENGE -> {
                int rem = activeConfig.scavenge_duration_ticks - stageTicks;
                msg = String.format("§8[DBG] §6SCAVENGE §7| Stage: §e%s §7| Closes in §c%.1fs",
                    activeConfig.id, rem / 20.0f);
            }
            default -> msg = "§8[DBG] §7State: " + stageState;
        }

        broadcastToArena(level, msg);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public boolean isActive() { return activeConfig != null; }
    public StageConfig getActiveConfig() { return activeConfig; }
    public StageState getStageState() { return stageState; }
    public WaveState getWaveState() { return waveState; }
    public int getCurrentWaveIndex() { return currentWaveIndex; }
    public int getTotalEnemiesInWave() { return totalEnemiesInWave; }
    public int getLivingEnemyCount() { return livingEnemies.size(); }
    public Set<UUID> getLivingEnemies() { return Collections.unmodifiableSet(livingEnemies); }
    public long getLastStageEndGameTime() { return lastStageEndGameTime; }
    public int getStageTicks() { return stageTicks; }
    public int getWaveTicks() { return waveTicks; }

    /** Returns ticks remaining until the next stage can trigger, or -1 if no stages remain. */
    public int getTicksUntilNextStage(ServerLevel level) {
        if (activeConfig != null) return 0;
        List<StageConfig> stages = new ArrayList<>(StageLoader.getAllStages());
        if (nextStageIndex >= stages.size()) return -1;
        StageConfig next = stages.get(nextStageIndex);
        long elapsed = level.getGameTime() - lastStageEndGameTime;
        long required = next.trigger_seconds * 20L;
        return (int) Math.max(0, required - elapsed);
    }

    // ── NBT persistence ──────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("LastStageEndGameTime", lastStageEndGameTime);
        tag.putInt("NextStageIndex", nextStageIndex);

        if (activeConfig != null) {
            tag.putString("ActiveStageId", activeConfig.id);
            tag.putString("StageState", stageState.name());
            if (waveState != null) tag.putString("WaveState", waveState.name());
            tag.putInt("StageTicks", stageTicks);
            tag.putInt("WaveTicks", waveTicks);
            tag.putInt("CurrentWaveIndex", currentWaveIndex);
            tag.putInt("TotalEnemiesInWave", totalEnemiesInWave);
            tag.putBoolean("ScavengeRewardFired", scavengeRewardFired);

            ListTag enemyList = new ListTag();
            for (UUID uuid : livingEnemies) {
                enemyList.add(StringTag.valueOf(uuid.toString()));
            }
            tag.put("LivingEnemies", enemyList);
        }

        return tag;
    }

    public static StageContext load(CompoundTag tag) {
        StageContext ctx = new StageContext();
        ctx.lastStageEndGameTime = tag.getLong("LastStageEndGameTime");
        ctx.nextStageIndex = tag.getInt("NextStageIndex");

        if (tag.contains("ActiveStageId")) {
            String stageId = tag.getString("ActiveStageId");
            StageLoader.getById(stageId).ifPresent(cfg -> {
                ctx.activeConfig = cfg;
                ctx.stageState = StageState.valueOf(tag.getString("StageState"));
                if (tag.contains("WaveState")) {
                    ctx.waveState = WaveState.valueOf(tag.getString("WaveState"));
                }
                ctx.stageTicks = tag.getInt("StageTicks");
                ctx.waveTicks = tag.getInt("WaveTicks");
                ctx.currentWaveIndex = tag.getInt("CurrentWaveIndex");
                ctx.totalEnemiesInWave = tag.getInt("TotalEnemiesInWave");
                ctx.scavengeRewardFired = tag.getBoolean("ScavengeRewardFired");

                ListTag enemyList = tag.getList("LivingEnemies", Tag.TAG_STRING);
                for (int i = 0; i < enemyList.size(); i++) {
                    try {
                        ctx.livingEnemies.add(UUID.fromString(enemyList.getString(i)));
                    } catch (IllegalArgumentException ignored) {}
                }
            });
        }

        return ctx;
    }
}
