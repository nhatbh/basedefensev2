package com.nhatbh.basedefensev2.api;

import com.nhatbh.basedefensev2.boss.client.BossResourceBarRegistry;
import com.nhatbh.basedefensev2.boss.core.BossComponent;
import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.boss.core.Phase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * Public API for querying boss information, phase progression, skill states,
 * passive resource bars, and boss identification in Base Defense v2.
 */
public class BossAPI {

    /**
     * Checks if the given entity is a registered Boss.
     *
     * @param entity Entity to check
     * @return true if the entity is a living boss
     */
    public static boolean isBoss(@Nullable Entity entity) {
        if (entity instanceof LivingEntity living) {
            return isBoss(living);
        }
        return false;
    }

    /**
     * Checks if the given living entity is a registered Boss.
     *
     * @param entity Living entity to check
     * @return true if entity is a registered boss component or has persistent boss NBT
     */
    public static boolean isBoss(@Nullable LivingEntity entity) {
        return BossManager.isBoss(entity);
    }

    /**
     * Gets the unique boss definition ID string for a boss entity (e.g. "titans_mantle").
     *
     * @param entity Living entity to query
     * @return Boss ID string or null if not a boss
     */
    @Nullable
    public static String getBossId(@Nullable LivingEntity entity) {
        if (!isBoss(entity)) {
            return null;
        }
        BossComponent comp = BossManager.get(entity);
        if (comp != null && comp.getDefinition() != null) {
            return comp.getDefinition().getId();
        }
        if (entity != null && entity.getPersistentData().contains("bdv2_boss_id")) {
            return entity.getPersistentData().getString("bdv2_boss_id");
        }
        return null;
    }

    /**
     * Retrieves the active runtime {@link BossComponent} for a boss entity.
     *
     * @param entity Living entity to query
     * @return BossComponent instance or null if not a boss
     */
    @Nullable
    public static BossComponent getBossComponent(@Nullable LivingEntity entity) {
        return BossManager.get(entity);
    }

    /**
     * Retrieves the static {@link BossDefinition} template for a boss entity.
     *
     * @param entity Living entity to query
     * @return BossDefinition instance or null if not a boss
     */
    @Nullable
    public static BossDefinition getBossDefinition(@Nullable LivingEntity entity) {
        BossComponent comp = getBossComponent(entity);
        return comp != null ? comp.getDefinition() : null;
    }

    // ── PHASE PROGRESSION ───────────────────────────────────────────────────

    /**
     * Gets the 0-indexed current phase of the boss.
     *
     * @param entity Living entity to query
     * @return Phase index (0, 1, 2...) or -1 if not a boss
     */
    public static int getCurrentPhaseIndex(@Nullable LivingEntity entity) {
        BossComponent comp = getBossComponent(entity);
        return comp != null ? comp.getCurrentPhaseIndex() : -1;
    }

    /**
     * Gets the total number of phases configured for this boss.
     *
     * @param entity Living entity to query
     * @return Total phases count or 0 if not a boss
     */
    public static int getTotalPhases(@Nullable LivingEntity entity) {
        BossDefinition def = getBossDefinition(entity);
        return (def != null && def.getPhases() != null) ? def.getPhases().size() : 0;
    }

    /**
     * Retrieves the currently active {@link Phase} object for the boss.
     *
     * @param entity Living entity to query
     * @return Active Phase or null if not a boss
     */
    @Nullable
    public static Phase getCurrentPhase(@Nullable LivingEntity entity) {
        BossComponent comp = getBossComponent(entity);
        return comp != null ? comp.getCurrentPhase() : null;
    }

    // ── SKILL & EXHAUSTION STATUS ───────────────────────────────────────────

    /**
     * Checks if the boss is currently in a poise-broken / stunned exhaustion state.
     *
     * @param entity Living entity to query
     * @return true if boss is exhausted
     */
    public static boolean isExhausted(@Nullable LivingEntity entity) {
        BossComponent comp = getBossComponent(entity);
        return comp != null && comp.getExhaustionTicks() > 0;
    }

    /**
     * Gets the remaining ticks of poise exhaustion on the boss.
     *
     * @param entity Living entity to query
     * @return Exhaustion ticks remaining or 0 if not exhausted
     */
    public static int getExhaustionTicks(@Nullable LivingEntity entity) {
        BossComponent comp = getBossComponent(entity);
        return comp != null ? comp.getExhaustionTicks() : 0;
    }

    /**
     * Checks if the boss is currently executing an active skill animation/sequence.
     *
     * @param entity Living entity to query
     * @return true if an active skill sequence is running
     */
    public static boolean isExecutingSkill(@Nullable LivingEntity entity) {
        BossComponent comp = getBossComponent(entity);
        return comp != null && comp.getCurrentSequence() != null && comp.getCurrentSequence().isRunning();
    }

    // ── PASSIVE RESOURCE BARS ────────────────────────────────────────────────

    /**
     * Checks if a custom passive resource bar is registered for this boss.
     *
     * @param entity Living entity to query
     * @return true if a passive resource bar is active
     */
    public static boolean hasPassiveResourceBar(@Nullable LivingEntity entity) {
        if (entity == null) return false;
        BossResourceBarRegistry.ResourceBarInfo info = BossResourceBarRegistry.getBar(entity);
        return info != null && info.maxSupplier.get() > 0;
    }

    /**
     * Gets the current value of the boss's registered passive resource bar.
     *
     * @param entity Living entity to query
     * @return Current resource value or 0.0f if none
     */
    public static float getPassiveResourceValue(@Nullable LivingEntity entity) {
        if (entity == null) return 0.0f;
        BossResourceBarRegistry.ResourceBarInfo info = BossResourceBarRegistry.getBar(entity);
        return info != null ? info.currentSupplier.get() : 0.0f;
    }

    /**
     * Gets the maximum value of the boss's registered passive resource bar.
     *
     * @param entity Living entity to query
     * @return Maximum resource value or 0.0f if none
     */
    public static float getMaxPassiveResourceValue(@Nullable LivingEntity entity) {
        if (entity == null) return 0.0f;
        BossResourceBarRegistry.ResourceBarInfo info = BossResourceBarRegistry.getBar(entity);
        return info != null ? info.maxSupplier.get() : 0.0f;
    }

    // ── ACTIONS ──────────────────────────────────────────────────────────────

    /**
     * Programmatically teleports a boss to a random valid player nearby.
     *
     * @param entity Living entity to teleport
     */
    public static void teleportToRandomPlayer(@Nullable LivingEntity entity) {
        if (isBoss(entity)) {
            BossManager.teleportBossToRandomPlayer(entity);
        }
    }
}
