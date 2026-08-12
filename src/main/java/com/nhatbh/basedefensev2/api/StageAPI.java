package com.nhatbh.basedefensev2.api;

import com.nhatbh.basedefensev2.config.SanctityConfig;
import com.nhatbh.basedefensev2.level.SealedVaultSavedData;
import com.nhatbh.basedefensev2.level.WorldLevelSavedData;
import com.nhatbh.basedefensev2.sanctity.data.AltarSavedData;
import com.nhatbh.basedefensev2.stage.ModDimensions;
import com.nhatbh.basedefensev2.stage.StageLoader;
import com.nhatbh.basedefensev2.stage.config.StageConfig;
import com.nhatbh.basedefensev2.stage.core.StageContext;
import com.nhatbh.basedefensev2.stage.core.StageState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Public API for querying and interacting with stage progression, world level,
 * base Sanctity/Grace, and equipment vaulting state in Base Defense v2.
 */
public class StageAPI {

    /**
     * Gets the StageContext for the arena level.
     */
    @Nullable
    private static StageContext getContext(Level level) {
        if (level == null) return null;
        MinecraftServer server = level.getServer();
        if (server == null) return null;

        ServerLevel arenaLevel = server.getLevel(ModDimensions.ARENA);
        if (arenaLevel == null) return null;

        return StageContext.getOrCreate(arenaLevel);
    }

    @Nullable
    private static ServerLevel getArenaLevel(Level level) {
        if (level == null) return null;
        MinecraftServer server = level.getServer();
        if (server == null) return null;
        return server.getLevel(ModDimensions.ARENA);
    }

    @Nullable
    private static ServerLevel getOverworld(Level level) {
        if (level == null) return null;
        MinecraftServer server = level.getServer();
        if (server == null) return null;
        return server.getLevel(Level.OVERWORLD);
    }

    // ── STAGE TRIAL STATUS ───────────────────────────────────────────────────

    /**
     * Checks if a stage trial is currently running (in WARMUP, ACTIVE, or SCAVENGE phase).
     */
    public static boolean isStageActive(Level level) {
        StageContext ctx = getContext(level);
        return ctx != null && ctx.isActive();
    }

    /**
     * Gets the current StageState of the arena.
     * Returns {@link StageState#ENDED} if context is unavailable.
     */
    public static StageState getStageState(Level level) {
        StageContext ctx = getContext(level);
        return ctx != null ? ctx.getStageState() : StageState.ENDED;
    }

    /**
     * Gets the ID of the currently active stage trial.
     * @return Stage ID string (e.g. "stage_1_goblin_invasion") or null if no active stage.
     */
    @Nullable
    public static String getActiveStageId(Level level) {
        StageContext ctx = getContext(level);
        if (ctx == null || !ctx.isActive() || ctx.getActiveConfig() == null) {
            return null;
        }
        return ctx.getActiveConfig().id;
    }

    /**
     * Gets the stage order number of the currently active stage trial.
     * @return Order number or -1 if no active stage.
     */
    public static int getActiveStageOrder(Level level) {
        StageContext ctx = getContext(level);
        if (ctx == null || !ctx.isActive() || ctx.getActiveConfig() == null) {
            return -1;
        }
        return ctx.getActiveConfig().order;
    }

    /**
     * Gets current wave index in the active stage trial (1-based).
     * @return Wave index or 0 if not currently in combat wave.
     */
    public static int getCurrentWaveIndex(Level level) {
        StageContext ctx = getContext(level);
        if (ctx == null || !ctx.isActive()) {
            return 0;
        }
        return ctx.getCurrentWaveIndex() + 1; // Convert 0-indexed to 1-based
    }

    /**
     * Gets the total number of waves in the currently active stage.
     * @return Total waves count or 0 if no active stage.
     */
    public static int getTotalWaveCount(Level level) {
        StageContext ctx = getContext(level);
        if (ctx == null || ctx.getActiveConfig() == null) {
            return 0;
        }
        return ctx.getActiveConfig().waves != null ? ctx.getActiveConfig().waves.size() : 0;
    }

    /**
     * Gets the count of living stage enemies currently remaining in the arena.
     */
    public static int getLivingEnemyCount(Level level) {
        StageContext ctx = getContext(level);
        return ctx != null ? ctx.getLivingEnemyCount() : 0;
    }

    // ── WORLD LEVEL & PROGRESSION ────────────────────────────────────────────

    /**
     * Gets the current World Level (0+).
     */
    public static int getWorldLevel(Level level) {
        ServerLevel overworld = getOverworld(level);
        if (overworld == null) return 0;
        return WorldLevelSavedData.get(overworld).getWorldLevel();
    }

    /**
     * Gets the ID of the next pending stage scheduled to run.
     * @return Stage ID string or null if none pending.
     */
    @Nullable
    public static String getPendingStageId(Level level) {
        StageContext ctx = getContext(level);
        ServerLevel arena = getArenaLevel(level);
        return (ctx != null && arena != null) ? ctx.getNextStageId(arena) : null;
    }

    /**
     * Gets remaining ticks until the pending stage auto-starts.
     * @return Remaining ticks or -1 if no auto-start countdown active.
     */
    public static int getTicksUntilNextStage(Level level) {
        StageContext ctx = getContext(level);
        ServerLevel arena = getArenaLevel(level);
        return (ctx != null && arena != null) ? ctx.getTicksUntilNextStage(arena) : -1;
    }

    // ── BASE SANCTITY & GRACE ────────────────────────────────────────────────

    /**
     * Gets current base Sanctity health points.
     */
    public static int getSanctity(Level level) {
        ServerLevel overworld = getOverworld(level);
        if (overworld == null) return 0;
        return AltarSavedData.get(overworld).getSanctity();
    }

    /**
     * Gets maximum base Sanctity capacity configured.
     */
    public static int getMaxSanctity() {
        return SanctityConfig.data.maxSanctity;
    }

    /**
     * Gets current base Grace points.
     */
    public static double getGrace(Level level) {
        ServerLevel overworld = getOverworld(level);
        if (overworld == null) return 0.0;
        return AltarSavedData.get(overworld).getGrace();
    }

    /**
     * Gets maximum base Grace capacity configured.
     */
    public static double getMaxGrace() {
        return SanctityConfig.data.maxGrace;
    }

    /**
     * Gets the number of retries consumed in the current stage attempt.
     */
    public static int getRetriesUsed(Level level) {
        ServerLevel overworld = getOverworld(level);
        if (overworld == null) return 0;
        return AltarSavedData.get(overworld).getRetriesUsed();
    }

    // ── EQUIPMENT VAULT ──────────────────────────────────────────────────────

    /**
     * Checks if a player's items are currently confiscated in the Sealed Vault.
     */
    public static boolean isPlayerEquipmentVaulted(ServerPlayer player) {
        if (player == null) return false;
        ServerLevel overworld = getOverworld(player.level());
        if (overworld == null) return false;
        return SealedVaultSavedData.get(overworld).isPlayerVaulted(player.getUUID());
    }

    /**
     * Gets the target stage order required to unlock a player's vaulted equipment.
     * @return Required stage order or -1 if player items are not vaulted.
     */
    public static int getPlayerVaultedStageOrder(ServerPlayer player) {
        if (player == null) return -1;
        ServerLevel overworld = getOverworld(player.level());
        if (overworld == null) return -1;
        return SealedVaultSavedData.get(overworld).getVaultedStageOrder(player.getUUID());
    }

    // ── ELEMENTAL & DISPLAY HELPERS ─────────────────────────────────────────

    /**
     * Gets the primary elemental affiliation of a stage config.
     */
    public static com.nhatbh.basedefensev2.elemental.ElementType getStageElement(StageConfig stage) {
        if (stage == null) return com.nhatbh.basedefensev2.elemental.ElementType.FIRE;
        return stage.getElement();
    }

    /**
     * Gets the ARGB integer color for a given ElementType.
     */
    public static int getElementColor(@Nullable com.nhatbh.basedefensev2.elemental.ElementType type) {
        if (type == null) return 0x888888;
        return type.getColor();
    }

    /**
     * Formats a raw boss skill ID into a clean display title.
     */
    public static String formatSkillName(String skillId) {
        return com.nhatbh.basedefensev2.boss.skills.ActiveSkill.formatSkillName(skillId);
    }

    // ── PROGRAMMATIC STAGE ACTIONS ────────────────────────────────────────────

    /**
     * Force starts a stage trial by stage ID.
     * @return true if stage was found and started successfully.
     */
    public static boolean forceStartStage(ServerLevel level, String stageId) {
        if (level == null || stageId == null) return false;
        ServerLevel arena = getArenaLevel(level);
        if (arena == null) return false;

        Optional<StageConfig> optConfig = StageLoader.getById(arena, stageId);
        if (optConfig.isEmpty()) return false;

        StageContext ctx = StageContext.getOrCreate(arena);
        ctx.forceStartStage(arena, optConfig.get());
        return true;
    }
}
