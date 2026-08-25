package com.nhatbh.basedefensev2.boss.core;

import com.nhatbh.basedefensev2.api.event.BossAdaptiveArmorEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks bullet hits per ammo type on boss entities and calculates dynamic vitality damage reduction.
 * Grants a 3-second grace period when a new ammo type is introduced before penalty accumulation begins.
 * Attacking with a new/different ammo type decays the boss's adaptation to other ammo types, encouraging ammo swapping.
 */
public class AdaptiveArmorTracker {

    public static final String NBT_KEY = "BossAdaptiveArmor";
    public static final String NBT_HIT_COUNTS = "HitCounts";
    public static final String NBT_LAST_AMMO = "LastAmmoType";
    public static final String NBT_PENALTY_PER_HIT = "PenaltyPerHit";
    public static final String NBT_MAX_CAP = "MaxReductionCap";
    public static final String NBT_RECOVERY_STEP = "CrossTypeRecoveryStep";
    public static final String NBT_NEW_AMMO_GRACE = "NewAmmoGraceTicks";
    public static final String NBT_AMMO_FIRST_HITS = "AmmoFirstHits";

    private final Map<String, Integer> hitCounts = new HashMap<>();
    private final Map<String, Long> ammoFirstHitTimes = new HashMap<>();
    private String lastAmmoType = null;

    private float penaltyPerHit = 0.05f;      // 5% reduction per hit after the 3-second grace period
    private float maxReductionCap = 0.95f;     // Up to 95% maximum reduction (5% min damage)
    private int crossTypeRecoveryStep = 2;     // Hits removed from other ammo types when damaging with a different ammo type
    private int newAmmoGraceTicks = 60;        // 3 seconds (60 ticks) grace period when a new ammo type is introduced

    public AdaptiveArmorTracker() {}

    public float getPenaltyPerHit() { return penaltyPerHit; }
    public void setPenaltyPerHit(float penalty) { this.penaltyPerHit = Math.max(0.0f, penalty); }

    public float getMaxReductionCap() { return maxReductionCap; }
    public void setMaxReductionCap(float cap) { this.maxReductionCap = Math.max(0.0f, Math.min(0.99f, cap)); }

    public int getCrossTypeRecoveryStep() { return crossTypeRecoveryStep; }
    public void setCrossTypeRecoveryStep(int step) { this.crossTypeRecoveryStep = Math.max(0, step); }

    public int getNewAmmoGraceTicks() { return newAmmoGraceTicks; }
    public void setNewAmmoGraceTicks(int ticks) { this.newAmmoGraceTicks = Math.max(0, ticks); }

    public int getHitCount(String ammoType) {
        if (ammoType == null) return 0;
        return hitCounts.getOrDefault(ammoType, 0);
    }

    public String getLastAmmoType() {
        return lastAmmoType;
    }

    public Map<String, Integer> getHitCounts() {
        return new HashMap<>(hitCounts);
    }

    /**
     * Calculates current vitality damage reduction multiplier for a given ammo type.
     * @return Multiplier between (1.0 - maxReductionCap) and 1.0 (e.g. 0.05 to 1.0)
     */
    public float getReductionMultiplier(String ammoType) {
        return getReductionMultiplier(ammoType, 0L);
    }

    public float getReductionMultiplier(String ammoType, long gameTime) {
        if (ammoType == null || ammoType.isEmpty()) return 1.0f;

        // 3-second grace period when a new ammo type is first introduced
        if (gameTime > 0 && ammoFirstHitTimes.containsKey(ammoType)) {
            long firstHit = ammoFirstHitTimes.get(ammoType);
            if (gameTime - firstHit <= newAmmoGraceTicks) {
                return 1.0f;
            }
        }

        int hits = hitCounts.getOrDefault(ammoType, 0);
        float reductionPercent = Math.min(maxReductionCap, hits * penaltyPerHit);
        return Math.max(1.0f - maxReductionCap, 1.0f - reductionPercent);
    }

    /**
     * Records a hit of a specific ammo type.
     * When damaging the boss with a different ammo type, decays adaptation penalties on all other ammo types.
     */
    public void recordHit(String ammoType, @Nullable CompoundTag persistentData) {
        recordHit(ammoType, persistentData, 0L);
    }

    public void recordHit(String ammoType, @Nullable CompoundTag persistentData, long gameTime) {
        if (ammoType == null || ammoType.isEmpty()) return;

        // Record first hit time for new ammo type grace window
        if (gameTime > 0 && !ammoFirstHitTimes.containsKey(ammoType)) {
            ammoFirstHitTimes.put(ammoType, gameTime);
        }

        // Do not accumulate penalty hits during the initial 3-second new ammo grace window
        boolean isNewAmmoGrace = false;
        if (gameTime > 0 && ammoFirstHitTimes.containsKey(ammoType)) {
            long firstHit = ammoFirstHitTimes.get(ammoType);
            if (gameTime - firstHit <= newAmmoGraceTicks) {
                isNewAmmoGrace = true;
            }
        }

        if (!isNewAmmoGrace) {
            int currentHits = hitCounts.getOrDefault(ammoType, 0);
            hitCounts.put(ammoType, currentHits + 1);
        }

        // When player switches to a new/different ammo type, decay adaptation penalties for all other ammo types down to a minimum floor of 1 hit count (5% reduction).
        // Full reset (0 hits / 3-second grace period re-activation) only occurs when the boss recovers from exhaustion (reset()).
        if (lastAmmoType != null && !lastAmmoType.equalsIgnoreCase(ammoType)) {
            for (Map.Entry<String, Integer> entry : hitCounts.entrySet()) {
                if (!entry.getKey().equalsIgnoreCase(ammoType)) {
                    int decayedHits = Math.max(1, entry.getValue() - crossTypeRecoveryStep);
                    entry.setValue(decayedHits);
                }
            }
        }

        this.lastAmmoType = ammoType;

        if (persistentData != null) {
            saveToNBT(persistentData);
        }
    }

    /**
     * Computes final vitality damage after applying adaptive reduction and firing BossAdaptiveArmorEvent.Calculate.
     */
    public float processVitalityDamage(LivingEntity boss, String ammoType, float rawVitalityDamage) {
        return processVitalityDamage(boss, ammoType, rawVitalityDamage, null);
    }

    public float processVitalityDamage(LivingEntity boss, String ammoType, float rawVitalityDamage, @javax.annotation.Nullable LivingEntity attacker) {
        if (boss == null || rawVitalityDamage <= 0.0f) return rawVitalityDamage;

        if (ammoType == null || ammoType.isEmpty()) {
            return rawVitalityDamage;
        }

        long gameTime = boss.level() != null ? boss.level().getGameTime() : 0L;

        float baseMultiplier = getReductionMultiplier(ammoType, gameTime);

        BossAdaptiveArmorEvent.Calculate event = new BossAdaptiveArmorEvent.Calculate(boss, ammoType, rawVitalityDamage, baseMultiplier);
        boolean canceled = MinecraftForge.EVENT_BUS.post(event);

        float finalMultiplier = canceled ? 1.0f : event.getReductionMultiplier();

        recordHit(ammoType, boss.getPersistentData(), gameTime);

        // Visual & audio feedback: Play 1 flash effect, 6-particle flame burst, and deflection sound when ineffectiveness reaches 70% threshold (reduction >= 70%, finalMultiplier <= 0.30f)
        if (finalMultiplier <= 0.30f && boss.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            double posX = boss.getX();
            double posY = boss.getY() + (boss.getBbHeight() * 0.5);
            double posZ = boss.getZ();

            // 1 Flash effect
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                    posX, posY, posZ,
                    1, 0.0, 0.0, 0.0, 0.0);

            // 6 Flame particles flying out
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    posX, posY, posZ,
                    6, 0.2, 0.2, 0.2, 0.1);

            // Audio deflection feedback
            serverLevel.playSound(null, posX, posY, posZ,
                    net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                    net.minecraft.sounds.SoundSource.HOSTILE,
                    0.8f, 1.4f + (boss.getRandom().nextFloat() * 0.3f));
        }

        // System message notification to player when ammo ineffectiveness reaches 95% threshold (finalMultiplier <= 0.051f)
        if (finalMultiplier <= 0.051f && attacker instanceof net.minecraft.world.entity.player.Player player) {
            long now = gameTime;
            String key = "bdv2_last_95_warn_" + player.getUUID();
            long lastWarn = boss.getPersistentData().getLong(key);
            if (now - lastWarn > 60) { // Cooldown of 3 seconds (60 ticks) to prevent chat spam
                boss.getPersistentData().putLong(key, now);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§4§oThe boss has adapted to " + ammoType + "... your attacks have become futile."
                ));
            }
        }

        return rawVitalityDamage * finalMultiplier;
    }

    /**
     * Resets all adaptation counters and persistent NBT tags. Called on exhaustion recovery.
     */
    public void reset(@Nullable CompoundTag persistentData) {
        hitCounts.clear();
        ammoFirstHitTimes.clear();
        lastAmmoType = null;
        if (persistentData != null) {
            persistentData.remove(NBT_KEY);
        }
    }

    // ── NBT Persistence ──────────────────────────────────────────────────────

    public void saveToNBT(CompoundTag persistentData) {
        if (persistentData == null) return;

        CompoundTag tag = new CompoundTag();
        CompoundTag hitsTag = new CompoundTag();
        CompoundTag firstHitsTag = new CompoundTag();

        for (Map.Entry<String, Integer> entry : hitCounts.entrySet()) {
            if (entry.getValue() > 0) {
                hitsTag.putInt(entry.getKey(), entry.getValue());
            }
        }
        tag.put(NBT_HIT_COUNTS, hitsTag);

        for (Map.Entry<String, Long> entry : ammoFirstHitTimes.entrySet()) {
            if (entry.getValue() > 0) {
                firstHitsTag.putLong(entry.getKey(), entry.getValue());
            }
        }
        tag.put(NBT_AMMO_FIRST_HITS, firstHitsTag);

        if (lastAmmoType != null) {
            tag.putString(NBT_LAST_AMMO, lastAmmoType);
        }

        tag.putFloat(NBT_PENALTY_PER_HIT, penaltyPerHit);
        tag.putFloat(NBT_MAX_CAP, maxReductionCap);
        tag.putInt(NBT_RECOVERY_STEP, crossTypeRecoveryStep);
        tag.putInt(NBT_NEW_AMMO_GRACE, newAmmoGraceTicks);

        persistentData.put(NBT_KEY, tag);
    }

    public void loadFromNBT(CompoundTag persistentData) {
        if (persistentData == null || !persistentData.contains(NBT_KEY)) return;

        CompoundTag tag = persistentData.getCompound(NBT_KEY);
        hitCounts.clear();
        ammoFirstHitTimes.clear();

        if (tag.contains(NBT_HIT_COUNTS)) {
            CompoundTag hitsTag = tag.getCompound(NBT_HIT_COUNTS);
            for (String key : hitsTag.getAllKeys()) {
                hitCounts.put(key, hitsTag.getInt(key));
            }
        }

        if (tag.contains(NBT_AMMO_FIRST_HITS)) {
            CompoundTag firstHitsTag = tag.getCompound(NBT_AMMO_FIRST_HITS);
            for (String key : firstHitsTag.getAllKeys()) {
                ammoFirstHitTimes.put(key, firstHitsTag.getLong(key));
            }
        }

        if (tag.contains(NBT_LAST_AMMO)) {
            lastAmmoType = tag.getString(NBT_LAST_AMMO);
        }

        if (tag.contains(NBT_PENALTY_PER_HIT)) penaltyPerHit = tag.getFloat(NBT_PENALTY_PER_HIT);
        if (tag.contains(NBT_MAX_CAP)) maxReductionCap = tag.getFloat(NBT_MAX_CAP);
        if (tag.contains(NBT_RECOVERY_STEP)) crossTypeRecoveryStep = tag.getInt(NBT_RECOVERY_STEP);
        if (tag.contains(NBT_NEW_AMMO_GRACE)) newAmmoGraceTicks = tag.getInt(NBT_NEW_AMMO_GRACE);
    }
}
