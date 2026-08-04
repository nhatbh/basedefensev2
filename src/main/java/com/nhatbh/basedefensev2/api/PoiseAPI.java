package com.nhatbh.basedefensev2.api;

import com.nhatbh.basedefensev2.api.event.PoiseBrokenEvent;
import com.nhatbh.basedefensev2.api.event.PoiseDamageEvent;
import com.nhatbh.basedefensev2.api.event.PoiseRecoveryEvent;
import com.nhatbh.basedefensev2.strength.EntityEvents;
import com.nhatbh.basedefensev2.strength.EntityStrengthData;
import com.nhatbh.basedefensev2.strength.ModAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import javax.annotation.Nullable;

/**
 * Public API for querying, damaging, modifying, and syncing the Poise (Strength) system on LivingEntities.
 */
public class PoiseAPI {

    public static boolean hasPoise(LivingEntity entity) {
        if (entity == null) return false;
        return EntityStrengthData.get(entity) != null;
    }

    @Nullable
    public static EntityStrengthData getPoiseData(LivingEntity entity) {
        if (entity == null) return null;
        return EntityStrengthData.get(entity);
    }

    public static float getCurrentPoise(LivingEntity entity) {
        EntityStrengthData data = getPoiseData(entity);
        return data != null ? data.currentStrength : 0.0f;
    }

    public static float getMaxPoise(LivingEntity entity) {
        EntityStrengthData data = getPoiseData(entity);
        return data != null ? data.maxStrength : 0.0f;
    }

    public static float getPoiseRatio(LivingEntity entity) {
        EntityStrengthData data = getPoiseData(entity);
        if (data == null || data.maxStrength <= 0) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, data.currentStrength / data.maxStrength));
    }

    public static boolean isExhausted(LivingEntity entity) {
        EntityStrengthData data = getPoiseData(entity);
        return data != null && data.currentStrength <= 0;
    }

    public static boolean isPoiseBroken(LivingEntity entity) {
        return isExhausted(entity);
    }

    public static int getRecoveryTicks(LivingEntity entity) {
        EntityStrengthData data = getPoiseData(entity);
        return data != null ? data.recoveryTicks : 0;
    }

    public static float getPoiseReductionValue(LivingEntity entity) {
        EntityStrengthData data = getPoiseData(entity);
        return data != null ? data.reductionValue : 0.0f;
    }

    public static boolean isPercentageBasedReduction(LivingEntity entity) {
        EntityStrengthData data = getPoiseData(entity);
        return data != null && data.isPercentageBased;
    }

    public static void initializePoise(LivingEntity entity, float maxPoise, float reductionValue, boolean isPercentageBased) {
        initializePoise(entity, maxPoise, maxPoise, reductionValue, isPercentageBased, 0);
    }

    public static void initializePoise(LivingEntity entity, float maxPoise, float currentPoise, float reductionValue, boolean isPercentageBased, int recoveryTicks) {
        if (entity == null || entity.level().isClientSide) return;
        EntityStrengthData data = new EntityStrengthData(maxPoise, currentPoise, reductionValue, isPercentageBased, recoveryTicks);
        data.save(entity);
        EntityStrengthData.sync(entity, data);
    }

    public static void setPoise(LivingEntity entity, float amount) {
        if (entity == null || entity.level().isClientSide) return;
        EntityStrengthData data = getPoiseData(entity);
        if (data == null) return;

        boolean wasHealthy = data.currentStrength > 0;
        data.currentStrength = Math.max(0.0f, Math.min(data.maxStrength, amount));

        if (wasHealthy && data.currentStrength <= 0) {
            data.recoveryTicks = 300;
            triggerPoiseBreak(entity);
        }

        data.save(entity);
        EntityStrengthData.sync(entity, data);
    }

    public static void setMaxPoise(LivingEntity entity, float maxPoise) {
        if (entity == null || entity.level().isClientSide) return;
        EntityStrengthData data = getPoiseData(entity);
        if (data == null) return;

        data.maxStrength = Math.max(0.0f, maxPoise);
        data.currentStrength = Math.min(data.currentStrength, data.maxStrength);
        data.save(entity);
        EntityStrengthData.sync(entity, data);
    }

    public static float damagePoise(LivingEntity entity, float amount) {
        return damagePoise(entity, amount, null, null);
    }

    public static float damagePoise(LivingEntity entity, float amount, @Nullable LivingEntity attacker, @Nullable DamageSource source) {
        return damagePoise(entity, amount, attacker, source, true);
    }

    /**
     * Damages entity poise, applying attribute multipliers if enableAttributeScaling is true.
     * Fires {@link PoiseDamageEvent} (cancelable) and {@link PoiseBrokenEvent} if depleted.
     * @return actual poise damage applied
     */
    public static float damagePoise(LivingEntity entity, float amount, @Nullable LivingEntity attacker, @Nullable DamageSource source, boolean enableAttributeScaling) {
        if (entity == null || entity.level().isClientSide || amount <= 0) return 0.0f;
        EntityStrengthData data = getPoiseData(entity);
        if (data == null || data.currentStrength <= 0) return 0.0f;

        float calculatedDamage = amount;

        if (enableAttributeScaling) {
            if (attacker != null && attacker.getAttributes().hasAttribute(ModAttributes.STRENGTH_DAMAGE_MULTIPLIER.get())) {
                calculatedDamage *= attacker.getAttributeValue(ModAttributes.STRENGTH_DAMAGE_MULTIPLIER.get());
            }
            if (entity.getAttributes().hasAttribute(ModAttributes.STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get())) {
                calculatedDamage *= entity.getAttributeValue(ModAttributes.STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get());
            }
        }

        PoiseDamageEvent event = new PoiseDamageEvent(entity, attacker, source, calculatedDamage);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            return 0.0f; // Canceled
        }

        float finalPoiseDamage = event.getAmount();
        if (finalPoiseDamage <= 0) return 0.0f;

        data.currentStrength = Math.max(0.0f, data.currentStrength - finalPoiseDamage);
        if (data.currentStrength <= 0) {
            data.currentStrength = 0.0f;
            data.recoveryTicks = 300;
            triggerPoiseBreak(entity);
        }

        data.save(entity);
        EntityStrengthData.sync(entity, data);
        return finalPoiseDamage;
    }

    public static float damagePoiseDirect(LivingEntity entity, float amount) {
        return damagePoise(entity, amount, null, null, false);
    }

    public static float depletePoisePercent(LivingEntity entity, float percentage) {
        EntityStrengthData data = getPoiseData(entity);
        if (data == null || data.maxStrength <= 0) return 0.0f;
        float amount = data.maxStrength * (percentage / 100.0f);
        return damagePoiseDirect(entity, amount);
    }

    public static void healPoise(LivingEntity entity, float amount) {
        if (entity == null || entity.level().isClientSide || amount <= 0) return;
        EntityStrengthData data = getPoiseData(entity);
        if (data == null) return;

        data.currentStrength = Math.min(data.maxStrength, data.currentStrength + amount);
        data.save(entity);
        EntityStrengthData.sync(entity, data);
    }

    public static void resetPoise(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return;
        EntityStrengthData data = getPoiseData(entity);
        if (data == null) return;

        data.currentStrength = data.maxStrength;
        data.recoveryTicks = 0;
        data.save(entity);
        EntityStrengthData.sync(entity, data);

        MinecraftForge.EVENT_BUS.post(new PoiseRecoveryEvent(entity));
    }

    public static float calculateMitigatedDamage(LivingEntity entity, float rawDamage) {
        EntityStrengthData data = getPoiseData(entity);
        if (data == null || data.currentStrength <= 0) return rawDamage;

        if (data.isPercentageBased) {
            return rawDamage * (1.0f - data.reductionValue);
        } else {
            return Math.max(0.0f, rawDamage - data.reductionValue);
        }
    }

    public static void syncPoise(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return;
        EntityStrengthData data = getPoiseData(entity);
        if (data != null) {
            EntityStrengthData.sync(entity, data);
        }
    }

    public static void triggerPoiseBreak(LivingEntity entity) {
        if (entity == null) return;
        MinecraftForge.EVENT_BUS.post(new EntityEvents.PoiseBroken(entity));
    }
}
