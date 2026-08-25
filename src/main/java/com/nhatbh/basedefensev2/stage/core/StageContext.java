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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
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
 * WARMUP → ACTIVE → SCAVENGE → ENDED
 *
 * Inner wave loop (delegated from ACTIVE):
 * SPAWNING → COMBAT → CLEARED (→ next wave or SCAVENGE)
 * → TIMEOUT (optional failure path)
 *
 * All side effects (spawning, rewards, cleanup) are handled by subsystems
 * listening to WaveEvents on MinecraftForge.EVENT_BUS.
 *
 * Also tracks the inter-stage timer:
 * latestStageEndGameTime = 0 means "world just created, no stage has ended
 * yet".
 * When (gameTime - latestStageEndGameTime) >= trigger_seconds * 20, the next
 * pending stage can begin.
 */
public class StageContext extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String SAVE_KEY = "basedefensev2_arena";

    // ── Inter-stage progression ──────────────────────────────────────────────
    /** Game-time tick when the last stage ended (0 = world creation). */
    private long lastStageEndGameTime = 0;
    /** Index into StageLoader.getAllStages() list for the next stage to run */
    private int nextStageIndex = 0;
    /** ID of the stage that has been randomly selected but not yet triggered */
    private String pendingStageId = null;
    /**
     * True if the arena schematic has been pasted and barrier created for this
     * world
     */
    private boolean arenaEstablished = false;
    /**
     * Transient flag to track if we've checked for player entry since server start
     */
    private transient boolean sessionRestartChecked = false;
    /**
     * Transient flag to track if cleanup has already found and removed entities in
     * this stage
     */
    private transient boolean cleanupSuccessful = false;
    /**
     * Transient flag: true once dynamic boss definitions have been re-registered
     * into ModBosses after a world reload. Reset each server session.
     */
    private transient boolean bossesReRegistered = false;
    /**
     * Transient: stage ID saved from NBT, resolved to activeConfig on first tick
     * once WorldStageSavedData is accessible.
     */
    private transient String pendingActiveStageId = null;

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

    /**
     * Total enemies spawned at the start of the current wave (set once in
     * registerEnemies, reset on new wave)
     */
    private int totalEnemiesInWave = 0;

    /** True on the first tick of SCAVENGE so rewards fire exactly once */
    private boolean scavengeRewardFired = false;

    /** Map of player UUIDs to their vote (true = YES, false = NO) */
    private final Map<UUID, Boolean> readyVotes = new HashMap<>();

    /** True if the stage / next stage timer is currently frozen */
    private boolean stageTimerFrozen = false;

    // ── Arena Barrier State ──────────────────────────────────────────────────
    private net.minecraft.core.BlockPos arenaBarrierCenter = null;
    private float arenaBarrierRadiusX = 0f;
    private float arenaBarrierRadiusZ = 0f;
    private List<net.minecraft.core.BlockPos> barrierBlockPositions = new ArrayList<>();
    private UUID activeBossUuid = null;
    private long bossSpawnTime = 0L;

    // ── SavedData factory ────────────────────────────────────────────────────

    public static StageContext getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                StageContext::load,
                StageContext::new,
                SAVE_KEY);
    }

    // ── Main tick entry point (called by ArenaDimensionTickHandler) ──────────

    /**
     * Called every server tick while the level is the arena dimension.
     */
    public void tick(ServerLevel level) {
        // Resolve deferred activeConfig from NBT load (needs WorldStageSavedData)
        if (pendingActiveStageId != null) {
            StageLoader.getById(level, pendingActiveStageId).ifPresentOrElse(
                cfg -> activeConfig = cfg,
                () -> LOGGER.warn("[StageContext] Could not resolve saved active stage '{}' from world data", pendingActiveStageId)
            );
            pendingActiveStageId = null;
        }

        // Handle server recovery: if an active combat stage was interrupted, restart it on first player join.
        // Intermission and Scavenge phases are explicitly excluded to preserve preparation/scavenging timers across saves.
        if (activeConfig != null && !sessionRestartChecked && !level.players().isEmpty()) {
            sessionRestartChecked = true;
            if (stageState != StageState.SCAVENGE) {
                restartActiveStage(level);
                return;
            }
        } else if (!sessionRestartChecked && !level.players().isEmpty()) {
            sessionRestartChecked = true;
        }

        if (activeConfig == null) {
            if (stageTimerFrozen) {
                lastStageEndGameTime++;
            }
            tryTriggerNextStage(level);
            return;
        }

        switch (stageState) {
            case WARMUP -> tickWarmup(level);
            case ACTIVE -> tickActive(level);
            case SCAVENGE -> tickScavenge(level);
            case ENDED -> {
            } // No-op; ArenaDimensionTickHandler will see activeConfig == null next cycle
        }

        setDirty();
    }

    // ── Trigger check ────────────────────────────────────────────────────────

    private void tryTriggerNextStage(ServerLevel level) {
        var worldData = com.nhatbh.basedefensev2.stage.generator.WorldStageSavedData.get(level);
        if (worldData.getAllStages().isEmpty()) {
            // First-ever generation for this world
            worldData.initializeForWorld(level.getSeed());
            bossesReRegistered = true; // generateWorldStages already registered bosses
        } else if (!bossesReRegistered) {
            // Stages loaded from NBT on world reload — re-register boss definitions
            worldData.reRegisterBosses();
            bossesReRegistered = true;
        }

        Collection<StageConfig> stages = StageLoader.getAllStages(level);
        if (stages.isEmpty()) return;

        List<StageConfig> sortedStages = stages.stream()
                .sorted(Comparator.comparingInt(s -> s.order))
                .toList();

        if (nextStageIndex >= sortedStages.size()) return; // All stages done

        StageConfig candidate = sortedStages.get(nextStageIndex);
        long elapsed = level.getGameTime() - lastStageEndGameTime;
        long required = candidate.trigger_seconds * 20L;

        if (elapsed < required)
            return;

        // Trigger!
        activeConfig = candidate;
        pendingStageId = null; // Clear pending state

        cleanupSuccessful = false;
        cleanupArenaMobs(level);

        stageState = StageState.WARMUP;
        waveState = null;
        stageTicks = 0;
        waveTicks = 0;
        currentWaveIndex = 0;
        livingEnemies.clear();
        scavengeRewardFired = false;

        prepareArenaAndPromptJoin(level);
        restoreAllPlayersItemDurability(level);
        setDirty();
    }

    private void prepareArenaAndPromptJoin(ServerLevel level) {
        broadcastToServer(level, "§c[The Rift] §4The earth trembles as the ritual grounds manifest! (Lag Warning)");
        try {
            // Load from assets folder in the mod jar
            java.io.InputStream schematicStream = com.nhatbh.basedefensev2.BaseDefenseMod.class
                    .getResourceAsStream("/assets/basedefensev2/schematics/arena.schem");
            String formatAlias = "sponge";
            if (schematicStream == null) {
                schematicStream = com.nhatbh.basedefensev2.BaseDefenseMod.class
                        .getResourceAsStream("/assets/basedefensev2/schematics/arena.schematic");
                formatAlias = "mcedit";
            }
            if (schematicStream != null) {
                com.nhatbh.basedefensev2.stage.utils.SchematicPaster.pasteSchematic(level,
                        com.sk89q.worldedit.math.BlockVector3.at(0, 101, 0), schematicStream, formatAlias);
            } else {
                LOGGER.warn(
                        "Arena schematic not found at assets/basedefensev2/schematics/arena.schem or arena.schematic");
            }
        } catch (Exception e) {
            LOGGER.error("Exception checking or pasting schematic", e);
        }

        com.nhatbh.basedefensev2.stage.utils.ArenaBarrierManager.createArenaBarrier(level);
        arenaEstablished = true;

        // Interactive JOIN message
        Component joinMsg = Component.literal("§6[The Rift] §eThe gates are open! ")
                .append(Component.literal("§l[ENTER]")
                        .withStyle(style -> style
                                .withColor(net.minecraft.ChatFormatting.GOLD)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/arena join"))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Enter the trial!")))));
        level.getServer().getPlayerList().broadcastSystemMessage(joinMsg, false);
    }

    // ── WARMUP ───────────────────────────────────────────────────────────────

    private void tickWarmup(ServerLevel level) {
        if (!stageTimerFrozen) {
            stageTicks++;
        }
        int remaining = activeConfig.warmup_ticks - stageTicks;

        // PERIODIC CLEANUP: Every 10 ticks for the first 100 ticks of warmup.
        // This ensures that asynchronously loaded entities are caught and removed.
        // Stops sweeping once a cleanup actually finds and removes entities.
        if (!cleanupSuccessful && stageTicks <= 100 && stageTicks % 10 == 0) {
            cleanupSuccessful = cleanupArenaMobs(level);
        }

        // Countdown broadcasts in the last 5 seconds
        if (remaining <= 100 && remaining >= 0 && remaining % 20 == 0) {
            broadcastToArena(level, "§e[The Rift] The trial commences in §c" + (remaining / 20) + "s§e...");
        }

        // 30 seconds before warmup ends (600 ticks): Broadcast warning if no players inside
        if (remaining == 600) {
            boolean hasPlayerInArena = level.players().stream().anyMatch(p -> p.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SPECTATOR);
            if (!hasPlayerInArena) {
                broadcastToServer(level, "§c[The Rift] §4The summoning ritual approaches! 30 seconds remain until forced entry!");
            }
        }

        // 15 seconds before warmup ends (300 ticks): Force teleport if arena is empty
        if (remaining == 300) {
            boolean hasPlayerInArena = level.players().stream().anyMatch(p -> p.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SPECTATOR);
            if (!hasPlayerInArena) {
                broadcastToServer(level, "§c[The Rift] §4No champions found in position! Commencing the grand summoning...");
                com.nhatbh.basedefensev2.stage.TeleportManager.forceTeleportAll(level);
            }
        }

        if (stageTicks >= activeConfig.warmup_ticks) {
            stageState = StageState.ACTIVE;
            stageTicks = 0;
            restoreAllPlayersItemDurability(level);
            startNextWave(level);
        }
    }

    private void broadcastToServer(ServerLevel level, String message) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    // ── ACTIVE (outer) ───────────────────────────────────────────────────────

    private void tickActive(ServerLevel level) {
        if (waveState == null)
            return;

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
        if (!stageTimerFrozen) {
            waveTicks++;
        }
        int remaining = 100 - waveTicks; // 5 seconds = 100 ticks

        if (remaining >= 0 && remaining % 20 == 0) {
            broadcastToArena(level, "§e[The Rift] The next onslaught arrives in §c" + (remaining / 20) + "s§e...");
        }

        if (waveTicks >= 100) {
            startNextWave(level);
        }
    }

    private void tickCombat(ServerLevel level) {
        if (!stageTimerFrozen) {
            waveTicks++;
        }

        // Fast prune every 10 ticks: remove entities that are loaded but dead/removed
        WaveConfig wave = currentWave();
        if (waveTicks % 10 == 0) {
            livingEnemies.removeIf(uuid -> {
                Entity entity = level.getEntity(uuid);
                if (entity == null) {
                    // Entity not loaded in this chunk — do not prune here;
                    // the periodic world scan below handles unloaded/missing entities.
                    return false;
                }
                return !entity.isAlive() || entity.isRemoved();
            });
        }

        // Periodic world-scan every 20 ticks: verify every tracked UUID still
        // has a living entity somewhere in the dimension. This catches mobs that
        // were silently discarded, chunk-unloaded and deleted, or otherwise
        // removed without triggering a death event.
        if (waveTicks % 20 == 0 && !livingEnemies.isEmpty()) {
            livingEnemies.removeIf(uuid -> {
                Entity entity = level.getEntity(uuid);
                return entity == null || !entity.isAlive() || entity.isRemoved();
            });

            if (livingEnemies.isEmpty()) {
                LOGGER.info("[StageContext] Periodic scan found no living enemies; advancing wave.");
                broadcastToArena(level, "§a[The Rift] The horde has been vanquished!");
                waveState = WaveState.CLEARED;
                tickWaveCleared(level);
                return;
            }
        }

        // Periodically enforce targeting on living players
        enforceMobTargeting(level);

        // Win condition (immediate, caught on same tick enemies die)
        if (livingEnemies.isEmpty()) {
            broadcastToArena(level, "§a[The Rift] The horde has been vanquished!");
            waveState = WaveState.CLEARED;
            tickWaveCleared(level);
            return;
        }

        // Lose condition
        if (wave.time_limit_ticks > 0 && waveTicks >= wave.time_limit_ticks) {
            broadcastToArena(level, "§c[The Rift] §4The hourglass has emptied! The trial hardens...");
            waveState = WaveState.TIMEOUT;
            tickWaveTimeout(level);
        }
    }

    private void tickWaveCleared(ServerLevel level) {
        if (currentWaveIndex + 1 < activeConfig.waves.size()) {
            // More waves remain -> WAIT
            currentWaveIndex++;
            waveState = WaveState.WAITING_NEXT_WAVE;
            waveTicks = 0;
        } else {
            // Final wave cleared → SCAVENGE
            currentWaveIndex++;
            stageState = StageState.SCAVENGE;
            stageTicks = 0;
            scavengeRewardFired = false;
        }
    }

    private void tickWaveTimeout(ServerLevel level) {
        if (currentWaveIndex + 1 < activeConfig.waves.size()) {
            currentWaveIndex++;
            waveState = WaveState.WAITING_NEXT_WAVE;
            waveTicks = 0;
        } else {
            // Final wave timeout: stay in TIMEOUT until all dead
            livingEnemies.removeIf(uuid -> level.getEntity(uuid) == null);

            if (livingEnemies.isEmpty()) {
                currentWaveIndex++;
                stageState = StageState.SCAVENGE;
                stageTicks = 0;
                scavengeRewardFired = false;
                return;
            }

            // Punishment ramping
            waveTicks++;
            if (waveTicks % 20 == 0) {
                int timeLimit = activeConfig.waves.get(currentWaveIndex).time_limit_ticks;
                float damage = (float) Math.pow(2, (int) ((waveTicks - timeLimit) / 1200));

                DamageSource ds = level.damageSources().magic();
                for (ServerPlayer player : level.players()) {
                    player.hurt(ds, damage);
                }

                // Visuals: flame particles on the arena floor (y=52)
                if (isArenaBarrierActive() && arenaBarrierCenter != null) {
                    double cx = arenaBarrierCenter.getX() + 0.5;
                    double cz = arenaBarrierCenter.getZ() + 0.5;
                    float rx = arenaBarrierRadiusX;
                    float rz = arenaBarrierRadiusZ;

                    // Spawn many particles to cover the floor (approx 200 per second)
                    for (int i = 0; i < 200; i++) {
                        double angle = level.random.nextDouble() * 2 * Math.PI;
                        double dist = Math.sqrt(level.random.nextDouble()); // uniform distribution
                        double px = cx + Math.cos(angle) * dist * rx;
                        double pz = cz + Math.sin(angle) * dist * rz;
                        // Randomize Y slightly above 52 to prevent clipping, and add small vertical
                        // speed
                        level.sendParticles(ParticleTypes.FLAME, px, 52.1, pz, 1, 0.1, 0.1, 0.1, 0.02);
                    }
                }
            }
        }
    }

    private void startNextWave(ServerLevel level) {
        boolean hasPlayerInArena = level.players().stream().anyMatch(p -> p.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SPECTATOR);
        if (!hasPlayerInArena) {
            com.nhatbh.basedefensev2.stage.TeleportManager.forceTeleportAll(level);
        }

        WaveConfig wave = currentWave();
        waveState = WaveState.SPAWNING;
        waveTicks = 0;
        // Wave carryover: do not clear livingEnemies.
        // New wave total includes any mobs already in the arena.
        totalEnemiesInWave = livingEnemies.size();

        broadcastToArena(level, "§6[The Rift] §eAssault §c" + (currentWaveIndex + 1)
                + "§e / §c" + activeConfig.waves.size() + " §eincoming!");

        MinecraftForge.EVENT_BUS.post(new WaveEvents.SpawnRequested(wave, this, level));
    }

    // ── SCAVENGE ─────────────────────────────────────────────────────────────

    private void tickScavenge(ServerLevel level) {
        // Tick 1: fire reward event exactly once
        if (!scavengeRewardFired) {
            scavengeRewardFired = true;
            WaveConfig finalWave = activeConfig.waves.get(activeConfig.waves.size() - 1);
            List<ServerPlayer> players = new ArrayList<>(level.getServer().getPlayerList().getPlayers());
            broadcastToArena(level, "§a[The Rift] §lTRIUMPH! §rClaim your spoils before the portal seals in §e"
                    + (activeConfig.scavenge_duration_ticks / 20) + "s§r.");

            // Interactive LEAVE message
            Component leaveMsg = Component.literal("§a[The Rift] §eVictory is yours! ")
                    .append(Component.literal("§l[RETURN]")
                            .withStyle(style -> style
                                    .withColor(net.minecraft.ChatFormatting.GREEN)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/arena leave"))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Return to the mortal realm!")))));
            level.getServer().getPlayerList().broadcastSystemMessage(leaveMsg, false);

            MinecraftForge.EVENT_BUS.post(new WaveEvents.LootPhaseStarted(finalWave, level, players));

            // Fully restore base Sanctity health upon completing the stage
            com.nhatbh.basedefensev2.sanctity.data.AltarSavedData altarData = com.nhatbh.basedefensev2.sanctity.data.AltarSavedData.get(level);
            altarData.setSanctity(com.nhatbh.basedefensev2.config.SanctityConfig.data.maxSanctity);

            // Increment World Level and notify server
            com.nhatbh.basedefensev2.level.WorldLevelSavedData worldLevelData = com.nhatbh.basedefensev2.level.WorldLevelSavedData.get(level);
            int newWorldLevel = worldLevelData.incrementWorldLevel();
            int newBaseLevel = com.nhatbh.basedefensev2.level.MobLevelConfig.getOverworldBaseLevel(newWorldLevel);

            broadcastToServer(level, String.format("§6§l[World Level Up!] §eThe World Level has risen to §b§lLevel %d§e! Overworld mobs spawn at base §aLv. %d§e!",
                    newWorldLevel, newBaseLevel));
        }

        if (!stageTimerFrozen) {
            stageTicks++;
        }

        // Countdown in the last 5 seconds
        int remaining = activeConfig.scavenge_duration_ticks - stageTicks;
        if (remaining <= 100 && remaining >= 0 && remaining % 20 == 0) {
            broadcastToArena(level, "§e[The Rift] The temporal anchor fades in §c" + (remaining / 20) + "s§e.");
        }

        if (stageTicks >= activeConfig.scavenge_duration_ticks) {
            enterEnded(level);
        }
    }

    // ── STAGE FAILURE & CLEANUP ──────────────────────────────────────────────

    public void endStageOnGameOver(ServerLevel level) {
        cleanupSuccessful = false;
        cleanupArenaMobs(level);
        stageState = StageState.ENDED;
        waveState = null;
        livingEnemies.clear();
        readyVotes.clear();
        setDirty();
    }

    public void skipWarmup(ServerLevel level) {
        if (stageState != StageState.WARMUP || activeConfig == null) return;

        com.nhatbh.basedefensev2.stage.TeleportManager.forceTeleportAll(level);
        broadcastToServer(level, "§6[The Rift] §a100% YES! Starting trial in 5 seconds...");
        
        // Fast forward stageTicks so 5 seconds (100 ticks) remain in warmup
        stageTicks = Math.max(stageTicks, activeConfig.warmup_ticks - 100);
        readyVotes.clear();
        setDirty();
    }

    public boolean processVote(ServerPlayer player, ServerLevel level, boolean voteYes) {
        if (activeConfig != null && stageState != StageState.WARMUP) {
            player.sendSystemMessage(Component.literal("§c[The Rift] No vote is currently active!"));
            return false;
        }

        if (activeConfig == null) {
            int ticksLeft = getTicksUntilNextStage(level);
            if (ticksLeft <= 0) {
                player.sendSystemMessage(Component.literal("§c[The Rift] No upcoming stage is pending or trial has begun!"));
                return false;
            }
            if (ticksLeft <= 6000) {
                player.sendSystemMessage(Component.literal("§c[The Rift] The next stage is already arriving in less than 5 minutes!"));
                return false;
            }
        }

        if (stageState == StageState.WARMUP) {
            boolean hasPlayerInArena = level.getServer().getPlayerList().getPlayers().stream()
                    .anyMatch(p -> p.level().dimension().equals(com.nhatbh.basedefensev2.stage.ModDimensions.ARENA) && 
                            p.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SPECTATOR);
            if (!hasPlayerInArena) {
                player.sendSystemMessage(Component.literal("§c[The Rift] A champion must enter the arena before voting!"));
                return false;
            }
        }

        String phaseName;
        if (activeConfig == null) {
            phaseName = "Inter-stage Countdown";
        } else {
            phaseName = "Warmup";
        }

        if (!voteYes) {
            readyVotes.clear();
            broadcastToServer(level, String.format("§c[The Rift] §a%s §cvoted §cNO§c! Vote to skip %s cancelled.",
                    player.getScoreboardName(), phaseName));
            setDirty();
            return false;
        }

        Boolean previousVote = readyVotes.put(player.getUUID(), true);
        if (previousVote != null && previousVote) {
            List<ServerPlayer> eligiblePlayers = level.getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> p.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SPECTATOR)
                    .toList();
            int totalRequired = Math.max(1, eligiblePlayers.size());
            long yesCount = eligiblePlayers.stream().filter(p -> Boolean.TRUE.equals(readyVotes.get(p.getUUID()))).count();
            player.sendSystemMessage(Component.literal(String.format("§c[The Rift] You already voted YES! (§a%d/%d§c)", yesCount, totalRequired)));
            return false;
        }

        List<ServerPlayer> eligiblePlayers = level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SPECTATOR)
                .toList();

        int totalRequired = Math.max(1, eligiblePlayers.size());

        long yesCount = eligiblePlayers.stream()
                .filter(p -> Boolean.TRUE.equals(readyVotes.get(p.getUUID())))
                .count();

        Component voteMessage = Component.literal(String.format("§6[The Rift] §a%s §evoted §aYES §eto skip %s! §7(§a%d/%d§7)\n",
                player.getScoreboardName(), phaseName, yesCount, totalRequired))
                .append(Component.literal(" §7[ "))
                .append(Component.literal("§a§l[YES]")
                        .withStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stage vote yes"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§aVote YES to skip")))))
                .append(Component.literal(" §7| "))
                .append(Component.literal("§c§l[NO]")
                        .withStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stage vote no"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§cVote NO to cancel")))))
                .append(Component.literal(" §7]"));

        level.getServer().getPlayerList().broadcastSystemMessage(voteMessage, false);

        if (yesCount >= totalRequired) {
            readyVotes.clear();
            if (activeConfig == null) {
                fastForwardToFiveMinutes(level);
            } else if (stageState == StageState.WARMUP) {
                skipWarmup(level);
            }
            return true;
        }

        setDirty();
        return false;
    }

    public void fastForwardToFiveMinutes(ServerLevel level) {
        if (activeConfig != null) return;
        List<Integer> orders = StageLoader.getSortedOrders(level);
        if (nextStageIndex >= orders.size()) return;

        int currentOrder = orders.get(nextStageIndex);
        StageConfig nextCandidate = null;
        if (pendingStageId != null) {
            nextCandidate = StageLoader.getById(level, pendingStageId).orElse(null);
        }
        if (nextCandidate == null) {
            List<StageConfig> candidates = StageLoader.getStagesForOrder(level, currentOrder);
            if (!candidates.isEmpty()) {
                nextCandidate = candidates.get(0);
            }
        }
        if (nextCandidate == null) return;

        long requiredTicks = nextCandidate.trigger_seconds * 20L;
        long targetElapsed = Math.max(0, requiredTicks - 6000L); // 5 minutes remaining = 6000 ticks

        lastStageEndGameTime = level.getGameTime() - targetElapsed;
        readyVotes.clear();
        broadcastToServer(level, "§6[The Rift] §a100% YES! Early stage participation confirmed! Next stage arrives in 5 minutes!");
        setDirty();
    }

    // ── ENDED ────────────────────────────────────────────────────────────────

    private void enterEnded(ServerLevel level) {
        stageState = StageState.ENDED;
        lastStageEndGameTime = level.getGameTime();

        List<Integer> orders = StageLoader.getSortedOrders(level);
        int completedStageOrder = (nextStageIndex < orders.size()) ? orders.get(nextStageIndex) : (nextStageIndex + 1);

        nextStageIndex++;

        AABB bounds = buildArenaBounds();
        MinecraftForge.EVENT_BUS.post(new WaveEvents.StageEnded(level, bounds));

        broadcastToArena(level, "§7[The Rift] The sanctuary has been restored.");

        // Restore confiscated equipment for eligible players
        com.nhatbh.basedefensev2.level.SealedVaultSavedData.get(level).restoreAllEligiblePlayers(level, completedStageOrder);

        // Null out active config so the context is idle
        activeConfig = null;
        stageState = null;
        waveState = null;
        pendingStageId = null; // Reset for next order
        setDirty();
    }

    public void resetForSoftGameOver(ServerLevel level) {
        cleanupArenaMobs(level);
        activeConfig = null;
        stageState = null;
        waveState = null;
        pendingStageId = null;
        nextStageIndex = 0;
        stageTicks = 0;
        lastStageEndGameTime = level.getGameTime();
        readyVotes.clear();
        rerollStages(level);
        setDirty();
    }

    /**
     * Forces immediate start of a custom StageConfig (overriding current stage progression).
     */
    public void forceStartStage(ServerLevel level, StageConfig customConfig) {
        if (!bossesReRegistered) {
            com.nhatbh.basedefensev2.stage.generator.WorldStageSavedData worldData = com.nhatbh.basedefensev2.stage.generator.WorldStageSavedData.get(level);
            worldData.reRegisterBosses();
            bossesReRegistered = true;
        }

        cleanupSuccessful = false;
        cleanupArenaMobs(level);

        activeConfig = customConfig;
        pendingStageId = null;

        stageState = StageState.WARMUP;
        waveState = null;
        stageTicks = 0;
        waveTicks = 0;
        currentWaveIndex = 0;
        livingEnemies.clear();
        scavengeRewardFired = false;

        prepareArenaAndPromptJoin(level);
        setDirty();
    }

    /**
     * Resets the currently active stage to the beginning (WARMUP phase).
     */
    private void restartActiveStage(ServerLevel level) {
        if (activeConfig == null)
            return;

        cleanupSuccessful = false;
        cleanupArenaMobs(level);

        stageState = StageState.WARMUP;
        waveState = null;
        stageTicks = 0;
        waveTicks = 0;
        currentWaveIndex = 0;
        livingEnemies.clear();
        scavengeRewardFired = false;

        broadcastToArena(level, "§6[The Rift] §eThe celestial cycle resets. Prepare anew!");
        setDirty();
    }

    /**
     * Despawns all living entities (monsters) in the arena dimension to provide a
     * clean slate.
     * 
     * @return true if at least one entity was removed.
     */
    private boolean cleanupArenaMobs(ServerLevel level) {
        // Define a huge AABB to capture all entities in the dimension
        net.minecraft.world.phys.AABB hugeArea = new net.minecraft.world.phys.AABB(-2000, -64, -2000, 2000, 320, 2000);

        List<net.minecraft.world.entity.LivingEntity> living = level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                hugeArea,
                entity -> !(entity instanceof net.minecraft.world.entity.player.Player));

        if (living.isEmpty()) {
            return false;
        }

        for (net.minecraft.world.entity.LivingEntity entity : living) {
            entity.discard();
        }

        // Ensure the tracking set is also cleared
        livingEnemies.clear();
        return true;
    }

    /**
     * Constantly forces all living wave mobs to target valid players in the arena.
     */
    private void enforceMobTargeting(ServerLevel level) {
        if (waveTicks % 200 != 0 || livingEnemies.isEmpty()) return;

        List<ServerPlayer> validPlayers = level.players().stream()
                .filter(com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper::isValidTarget)
                .toList();

        if (validPlayers.isEmpty()) return;

        for (UUID uuid : livingEnemies) {
            net.minecraft.world.entity.Entity entity = level.getEntity(uuid);
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                net.minecraft.world.entity.LivingEntity target = mob.getTarget();
                if (target == null || !target.isAlive() || !(target instanceof net.minecraft.world.entity.player.Player)) {
                    ServerPlayer closest = null;
                    double minSq = Double.MAX_VALUE;
                    for (ServerPlayer player : validPlayers) {
                        double distSq = mob.distanceToSqr(player);
                        if (distSq < minSq) {
                            minSq = distSq;
                            closest = player;
                        }
                    }
                    if (closest != null) {
                        mob.setTarget(closest);
                    }
                }
            }
        }
    }

    /**
     * OP Command: Rerolls the world stage selection sequence and clears pending selection.
     * Clears the existing saved stages so initializeForWorld can regenerate them.
     */
    public void rerollStages(ServerLevel level) {
        var worldData = com.nhatbh.basedefensev2.stage.generator.WorldStageSavedData.get(level);
        worldData.clearAndRegenerate(level.getSeed() ^ System.currentTimeMillis());
        pendingStageId = null;
        setDirty();
    }

    /**
     * OP Command: Fast forwards the inter-stage / intermission / warmup / active wave timer by a specified number of seconds.
     */
    public void fastForwardTimer(ServerLevel level, int seconds) {
        int ticksToAdvance = seconds * 20;

        if (activeConfig != null) {
            if (stageState == StageState.WARMUP || stageState == StageState.SCAVENGE) {
                stageTicks += ticksToAdvance;
            } else if (stageState == StageState.ACTIVE) {
                waveTicks += ticksToAdvance;
            }
        } else {
            lastStageEndGameTime -= ticksToAdvance;
        }
        setDirty();
    }

    /**
     * OP Command: Cancels the active stage (if any) and postpones the upcoming stage by a specified number of seconds,
     * allowing players to leave the arena freely.
     */
    public void postponeTimer(ServerLevel level, int seconds) {
        if (activeConfig != null) {
            cleanupSuccessful = false;
            cleanupArenaMobs(level);
            clearArenaBarrier();
            activeConfig = null;
            stageState = null;
            waveState = null;
            stageTicks = 0;
            waveTicks = 0;
            currentWaveIndex = 0;
            livingEnemies.clear();
            readyVotes.clear();
            scavengeRewardFired = false;
        }

        long postponeTicks = Math.max(1, seconds) * 20L;

        StageConfig nextCandidate = null;
        if (pendingStageId != null) {
            nextCandidate = StageLoader.getById(level, pendingStageId).orElse(null);
        }
        if (nextCandidate == null) {
            List<Integer> orders = StageLoader.getSortedOrders(level);
            if (nextStageIndex < orders.size()) {
                int currentOrder = orders.get(nextStageIndex);
                List<StageConfig> candidates = StageLoader.getStagesForOrder(level, currentOrder);
                if (!candidates.isEmpty()) {
                    nextCandidate = candidates.get(0);
                }
            }
        }

        long requiredTicks = (nextCandidate != null) ? nextCandidate.trigger_seconds * 20L : postponeTicks;
        lastStageEndGameTime = level.getGameTime() - Math.max(0L, requiredTicks - postponeTicks);

        int minutes = seconds / 60;
        int secs = seconds % 60;
        String timeStr = minutes > 0 ? minutes + "m " + secs + "s" : secs + "s";
        broadcastToServer(level, String.format("§6[The Rift OP] §cThe stage trial has been postponed for %s! Players may exit using /arena leave.", timeStr));

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
        }
        setDirty();
    }

    /**
     * Called by LivingDeathEvent listener when an entity dies in the arena.
     * Pruning also happens passively in tickCombat().
     */
    public boolean isLastWave() {
        return activeConfig != null && currentWaveIndex >= activeConfig.waves.size() - 1;
    }

    public void onEntityDied(UUID uuid) {
        livingEnemies.remove(uuid);
        setDirty();
    }

    public void onEntityDied(UUID uuid, net.minecraft.world.entity.LivingEntity entity, ServerLevel level) {
        livingEnemies.remove(uuid);

        if (isLastWave() && entity != null && com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)) {
            LOGGER.info("[StageContext] Last wave boss defeated! Automatically winning stage.");
            broadcastToArena(level, "§a[The Rift] The Stage Boss has fallen! Victory is yours!");

            // Discard all remaining wave mobs
            for (UUID mobUuid : new ArrayList<>(livingEnemies)) {
                Entity mob = level.getEntity(mobUuid);
                if (mob != null && mob.isAlive() && !(mob instanceof net.minecraft.world.entity.player.Player)) {
                    mob.discard();
                }
            }
            livingEnemies.clear();
            waveState = WaveState.CLEARED;
            tickWaveCleared(level);
        }

        setDirty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private WaveConfig currentWave() {
        return activeConfig.waves.get(currentWaveIndex);
    }

    private AABB buildArenaBounds() {
        if (activeConfig == null)
            return new AABB(-50, -64, -50, 50, 320, 50);
        StageConfig.SpawnArea area = getSpawnArea();
        double r = area.radius + 10; // extra margin for cleanup
        return new AABB(area.x - r, 0, area.z - r, area.x + r, 256, area.z + r);
    }

    private void broadcastToArena(ServerLevel level, String message) {
        level.players().forEach(p -> p.sendSystemMessage(Component.literal(message)));
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public boolean isActive() {
        return activeConfig != null;
    }

    public StageConfig getActiveConfig() {
        return activeConfig;
    }

    public StageState getStageState() {
        return stageState;
    }

    public WaveState getWaveState() {
        return waveState;
    }

    public int getCurrentWaveIndex() {
        return currentWaveIndex;
    }

    public int getTotalEnemiesInWave() {
        return totalEnemiesInWave;
    }

    public int getLivingEnemyCount() {
        return livingEnemies.size();
    }

    public Set<UUID> getLivingEnemies() {
        return Collections.unmodifiableSet(livingEnemies);
    }

    public int getStageTicks() {
        return stageTicks;
    }

    public int getWaveTicks() {
        return waveTicks;
    }



    public StageConfig.SpawnArea getSpawnArea() {
        StageConfig.SpawnArea area = new StageConfig.SpawnArea();
        if (isArenaBarrierActive() && arenaBarrierCenter != null) {
            area.x = arenaBarrierCenter.getX() + 0.5;
            area.y = arenaBarrierCenter.getY();
            area.z = arenaBarrierCenter.getZ() + 0.5;
        } else {
            area.x = 0.5;
            area.y = 52;
            area.z = 0.5;
        }
        area.radius = activeConfig != null ? activeConfig.spawn_radius : 25;
        return area;
    }

    // ── Barrier Getters/Setters ──────────────────────────────────────────────
    public boolean isArenaBarrierActive() {
        return arenaBarrierCenter != null;
    }

    public net.minecraft.core.BlockPos getArenaBarrierCenter() {
        return arenaBarrierCenter;
    }

    public float getArenaBarrierRadiusX() {
        return arenaBarrierRadiusX;
    }

    public float getArenaBarrierRadiusZ() {
        return arenaBarrierRadiusZ;
    }

    public float getArenaBarrierRadius() {
        return (arenaBarrierRadiusX + arenaBarrierRadiusZ) / 2.0f;
    }

    public List<net.minecraft.core.BlockPos> getBarrierBlockPositions() {
        return barrierBlockPositions;
    }

    public UUID getActiveBossUuid() {
        return activeBossUuid;
    }

    public void setArenaBarrierCenter(net.minecraft.core.BlockPos center) {
        this.arenaBarrierCenter = center;
        setDirty();
    }

    public void setArenaBarrierEllipse(float rx, float rz) {
        this.arenaBarrierRadiusX = rx;
        this.arenaBarrierRadiusZ = rz;
        setDirty();
    }

    public void setBarrierBlockPositions(List<net.minecraft.core.BlockPos> blocks) {
        this.barrierBlockPositions = blocks;
        setDirty();
    }

    public void clearArenaBarrier() {
        this.arenaBarrierCenter = null;
        this.barrierBlockPositions.clear();
        this.activeBossUuid = null;
        setDirty();
    }

    public void setActiveBossUuid(UUID uuid, long time) {
        this.activeBossUuid = uuid;
        this.bossSpawnTime = time;
        setDirty();
    }

    public String getNextStageId(ServerLevel level) {
        if (pendingStageId != null) return pendingStageId;
        List<Integer> orders = StageLoader.getSortedOrders(level);
        if (nextStageIndex >= orders.size()) return null;
        int currentOrder = orders.get(nextStageIndex);
        List<StageConfig> candidates = StageLoader.getStagesForOrder(level, currentOrder);
        if (candidates.isEmpty()) return null;
        return candidates.get(0).id;
    }

    /**
     * Returns ticks remaining until the next stage can trigger, or -1 if no stages
     * remain.
     */
    public int getTicksUntilNextStage(ServerLevel level) {
        if (activeConfig != null)
            return 0;

        List<Integer> orders = StageLoader.getSortedOrders(level);
        if (nextStageIndex >= orders.size())
            return -1;

        int currentOrder = orders.get(nextStageIndex);
        StageConfig nextCandidate = null;

        if (pendingStageId != null) {
            nextCandidate = StageLoader.getById(level, pendingStageId).orElse(null);
        }

        if (nextCandidate == null) {
            List<StageConfig> candidates = StageLoader.getStagesForOrder(level, currentOrder);
            if (candidates.isEmpty())
                return -1;
            nextCandidate = candidates.get(0); // Use first as fallback/preview
        }

        long elapsed = level.getGameTime() - lastStageEndGameTime;
        long required = nextCandidate.trigger_seconds * 20L;
        return (int) Math.max(0, required - elapsed);
    }

    // ── NBT persistence ──────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("LastStageEndGameTime", lastStageEndGameTime);
        tag.putInt("NextStageIndex", nextStageIndex);
        tag.putBoolean("ArenaEstablished", arenaEstablished);
        tag.putBoolean("StageTimerFrozen", stageTimerFrozen);
        if (pendingStageId != null) {
            tag.putString("PendingStageId", pendingStageId);
        }

        if (activeConfig != null) {
            tag.putString("ActiveStageId", activeConfig.id);
            tag.putString("StageState", stageState.name());
            if (waveState != null)
                tag.putString("WaveState", waveState.name());
            tag.putInt("StageTicks", stageTicks);
            tag.putInt("WaveTicks", waveTicks);
            tag.putInt("CurrentWaveIndex", currentWaveIndex);
            tag.putBoolean("ScavengeRewardFired", scavengeRewardFired);

            ListTag enemyList = new ListTag();
            for (UUID uuid : livingEnemies) {
                enemyList.add(StringTag.valueOf(uuid.toString()));
            }
            tag.put("LivingEnemies", enemyList);
        }

        if (arenaBarrierCenter != null) {
            tag.putLong("ArenaBarrierCenter", arenaBarrierCenter.asLong());
            tag.putFloat("ArenaBarrierRadiusX", arenaBarrierRadiusX);
            tag.putFloat("ArenaBarrierRadiusZ", arenaBarrierRadiusZ);

            long[] blockPosLogs = new long[barrierBlockPositions.size()];
            for (int i = 0; i < barrierBlockPositions.size(); i++) {
                blockPosLogs[i] = barrierBlockPositions.get(i).asLong();
            }
            tag.putLongArray("BarrierBlockPositions", blockPosLogs);

            if (activeBossUuid != null) {
                tag.putUUID("ActiveBossUuid", activeBossUuid);
                tag.putLong("BossSpawnTime", bossSpawnTime);
            }
        }

        return tag;
    }

    public static StageContext load(CompoundTag tag) {
        StageContext ctx = new StageContext();
        ctx.lastStageEndGameTime = tag.getLong("LastStageEndGameTime");
        ctx.nextStageIndex = tag.getInt("NextStageIndex");
        ctx.arenaEstablished = tag.getBoolean("ArenaEstablished");
        ctx.stageTimerFrozen = tag.getBoolean("StageTimerFrozen");
        if (tag.contains("PendingStageId")) {
            ctx.pendingStageId = tag.getString("PendingStageId");
        }

        if (tag.contains("ActiveStageId")) {
            String stageId = tag.getString("ActiveStageId");
            // Defer config lookup to first tick() when ServerLevel/WorldStageSavedData is available
            ctx.pendingActiveStageId = stageId;
            // Load all state that doesn't depend on the config object immediately
            try {
                ctx.stageState = StageState.valueOf(tag.getString("StageState"));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[StageContext] Legacy or unknown StageState '{}' found in NBT, resetting to WARMUP", tag.getString("StageState"));
                ctx.stageState = StageState.WARMUP;
            }
            if (tag.contains("WaveState")) {
                try {
                    ctx.waveState = WaveState.valueOf(tag.getString("WaveState"));
                } catch (IllegalArgumentException ignored) {
                    ctx.waveState = null;
                }
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
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (tag.contains("ArenaBarrierCenter")) {
            ctx.arenaBarrierCenter = net.minecraft.core.BlockPos.of(tag.getLong("ArenaBarrierCenter"));
            ctx.arenaBarrierRadiusX = tag.getFloat("ArenaBarrierRadiusX");
            ctx.arenaBarrierRadiusZ = tag.getFloat("ArenaBarrierRadiusZ");

            long[] logs = tag.getLongArray("BarrierBlockPositions");
            for (long pos : logs) {
                ctx.barrierBlockPositions.add(net.minecraft.core.BlockPos.of(pos));
            }

            if (tag.hasUUID("ActiveBossUuid")) {
                ctx.activeBossUuid = tag.getUUID("ActiveBossUuid");
                ctx.bossSpawnTime = tag.getLong("BossSpawnTime");
            }
        }

        return ctx;
    }

    public static void restoreAllPlayersItemDurability(ServerLevel level) {
        if (level == null || level.getServer() == null) return;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            restorePlayerItemDurability(player);
        }
    }

    public static void restorePlayerItemDurability(ServerPlayer player) {
        if (player == null) return;

        // 1. Restore main inventory items (36 slots: hotbar + inventory)
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.isDamageableItem()) {
                stack.setDamageValue(0);
            }
        }

        // 2. Restore equipped armor items (4 slots: feet, legs, chest, head)
        for (ItemStack stack : player.getInventory().armor) {
            if (!stack.isEmpty() && stack.isDamageableItem()) {
                stack.setDamageValue(0);
            }
        }

        // 3. Restore offhand item (1 slot)
        for (ItemStack stack : player.getInventory().offhand) {
            if (!stack.isEmpty() && stack.isDamageableItem()) {
                stack.setDamageValue(0);
            }
        }

        // 4. Restore Curios items if installed
        if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
            try {
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                    var curiosMap = handler.getCurios();
                    for (var entry : curiosMap.entrySet()) {
                        var stackHandler = entry.getValue().getStacks();
                        for (int i = 0; i < stackHandler.getSlots(); i++) {
                            ItemStack curioStack = stackHandler.getStackInSlot(i);
                            if (!curioStack.isEmpty() && curioStack.isDamageableItem()) {
                                curioStack.setDamageValue(0);
                            }
                        }
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    public boolean isStageTimerFrozen() {
        return stageTimerFrozen;
    }

    public boolean toggleStageTimerFreeze() {
        this.stageTimerFrozen = !this.stageTimerFrozen;
        setDirty();
        return this.stageTimerFrozen;
    }

    public void setStageTimerFrozen(boolean frozen) {
        this.stageTimerFrozen = frozen;
        setDirty();
    }
}
