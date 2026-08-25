package com.nhatbh.basedefensev2.api;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.api.event.PoiseBrokenEvent;
import com.nhatbh.basedefensev2.api.event.PoiseDamageEvent;
import com.nhatbh.basedefensev2.api.event.PoiseRecoveryEvent;
import com.nhatbh.basedefensev2.strength.EntityEvents;
import com.nhatbh.basedefensev2.strength.EntityStrengthData;
import com.nhatbh.basedefensev2.strength.ModAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import javax.annotation.Nullable;

/**
 * Public API for querying, damaging, modifying, and syncing the Poise (Strength) system on LivingEntities.
 */
@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PoiseAPI {

    public static boolean ENABLE_DEBUG_LOGGING = false;

    public static class Priority {
        public static final int LOW = 25;
        public static final int NORMAL = 50;
        public static final int HIGH = 75;
        public static final int HIGHEST = 100;
    }

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
        if (entity == null) return false;
        EntityStrengthData data = getPoiseData(entity);
        boolean isPoiseExhausted = data != null && (data.currentStrength <= 0 || data.recoveryTicks > 0);
        boolean isBossExhausted = false;
        if (com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)) {
            com.nhatbh.basedefensev2.boss.core.BossComponent comp = com.nhatbh.basedefensev2.boss.core.BossManager.get(entity);
            if (comp != null && comp.getExhaustionTicks() > 0) {
                isBossExhausted = true;
            }
        }
        return isPoiseExhausted || isBossExhausted;
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

    public static double calculateCorrosionMultiplier(int corrosionHits, double baseArmor) {
        if (corrosionHits <= 0) return 1.0;
        double requiredHits = 25.0 + (Math.max(0.0, baseArmor) * 0.45);
        double ratio = Math.min(1.0, (double) corrosionHits / requiredHits);
        double progress = ratio * ratio;
        return Math.max(0.0, 1.0 - progress);
    }

    public static int getCorrosionHits(@Nullable LivingEntity entity) {
        if (entity == null) return 0;
        if (com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)) {
            com.nhatbh.basedefensev2.boss.core.BossComponent comp = com.nhatbh.basedefensev2.boss.core.BossManager.get(entity);
            return comp != null ? comp.getCorrosionHits() : 0;
        }
        return entity.getPersistentData().getInt("bdv2_corrosion_hits");
    }

    public static final java.util.UUID CORROSION_ARMOR_MOD_UUID = com.nhatbh.basedefensev2.utils.UUIDHelper.generateAttributeModifierUUID("poise_corrosion", "armor");

    public static double getBaseArmorValue(@Nullable LivingEntity entity) {
        if (entity == null) return 0.0;
        var armorAttr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (armorAttr == null) return 0.0;
        var mod = armorAttr.getModifier(CORROSION_ARMOR_MOD_UUID);
        if (mod != null) {
            double val = armorAttr.getValue();
            double factor = 1.0 + mod.getAmount();
            return factor > 0 ? val / factor : val;
        }
        return armorAttr.getValue();
    }

    public static double getCorrosionMultiplier(@Nullable LivingEntity entity) {
        if (entity == null) return 1.0;
        double baseArmor = com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)
                ? com.nhatbh.basedefensev2.boss.core.BossManager.calculateBossArmor(entity)
                : getBaseArmorValue(entity);
        int hits = getCorrosionHits(entity);
        return calculateCorrosionMultiplier(hits, baseArmor);
    }

    public static void applyCorrosionAttributeModifier(@Nullable LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return;
        var armorAttr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (armorAttr == null) return;

        double mult = getCorrosionMultiplier(entity);
        double reductionRatio = mult - 1.0;

        if (armorAttr.getModifier(CORROSION_ARMOR_MOD_UUID) != null) {
            armorAttr.removeModifier(CORROSION_ARMOR_MOD_UUID);
        }

        if (reductionRatio < 0.0) {
            armorAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    CORROSION_ARMOR_MOD_UUID,
                    "CorrosionArmorModifier",
                    reductionRatio,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }

    public static void removeCorrosionAttributeModifier(@Nullable LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return;
        var armorAttr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (armorAttr != null && armorAttr.getModifier(CORROSION_ARMOR_MOD_UUID) != null) {
            armorAttr.removeModifier(CORROSION_ARMOR_MOD_UUID);
        }
    }

    public static boolean isFullyCorroded(@Nullable LivingEntity entity) {
        if (entity == null) return false;
        double baseArmor = com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)
                ? com.nhatbh.basedefensev2.boss.core.BossManager.calculateBossArmor(entity)
                : getBaseArmorValue(entity);
        if (baseArmor <= 0) return false;
        return getCorrosionMultiplier(entity) <= 0.0;
    }

    public static void clearBeneficialEffects(@Nullable LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return;
        java.util.List<net.minecraft.world.effect.MobEffect> beneficial = new java.util.ArrayList<>();
        for (net.minecraft.world.effect.MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL) {
                beneficial.add(instance.getEffect());
            }
        }
        for (net.minecraft.world.effect.MobEffect effect : beneficial) {
            entity.removeEffect(effect);
        }
    }

    /**
     * Calculates maximum poise for a mob based on its max HP.
     * Starts at 100% of max health for baseline mobs (20 HP) and smoothly curves down to 50% at 10,000 HP.
     */
    public static float calculateMobMaxPoise(float maxHp) {
        if (maxHp <= 0) return 0.0f;
        float baseHp = 20.0f;
        if (maxHp <= baseHp) {
            return maxHp;
        }
        float targetHp = 10000.0f;
        if (maxHp >= targetHp) {
            return maxHp * 0.5f;
        }
        float t = (maxHp - baseHp) / (targetHp - baseHp);
        float ratio = 1.0f - 0.5f * (float) Math.pow(t, 0.6);
        return maxHp * ratio;
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
        return damagePoise(entity, amount, amount, null, null, true, "BaseDefense", Priority.NORMAL);
    }

    public static float damagePoise(LivingEntity entity, float poiseAmount, float vitalityAmount) {
        return damagePoise(entity, poiseAmount, vitalityAmount, null, null, true, "BaseDefense", Priority.NORMAL);
    }

    public static float damagePoise(LivingEntity entity, float amount, @Nullable LivingEntity attacker, @Nullable DamageSource source) {
        return damagePoise(entity, amount, amount, attacker, source, true, "BaseDefense", Priority.NORMAL);
    }

    public static float damagePoise(LivingEntity entity, float poiseAmount, float vitalityAmount, @Nullable LivingEntity attacker, @Nullable DamageSource source) {
        return damagePoise(entity, poiseAmount, vitalityAmount, attacker, source, true, "BaseDefense", Priority.NORMAL);
    }

    public static float damagePoise(LivingEntity entity, float amount, @Nullable LivingEntity attacker, @Nullable DamageSource source, boolean enableAttributeScaling) {
        return damagePoise(entity, amount, amount, attacker, source, enableAttributeScaling, "BaseDefense", Priority.NORMAL);
    }

    public static float damagePoise(LivingEntity entity, float poiseAmount, float vitalityAmount, @Nullable LivingEntity attacker, @Nullable DamageSource source, boolean enableAttributeScaling) {
        return damagePoise(entity, poiseAmount, vitalityAmount, attacker, source, enableAttributeScaling, "BaseDefense", Priority.NORMAL);
    }

    public static float damagePoise(LivingEntity entity, float poiseAmount, float vitalityAmount, @Nullable LivingEntity attacker, @Nullable DamageSource source, boolean enableAttributeScaling, @Nullable String sourceMod) {
        return damagePoise(entity, poiseAmount, vitalityAmount, attacker, source, enableAttributeScaling, sourceMod, Priority.NORMAL);
    }

    public static float damagePoise(LivingEntity entity, float poiseAmount, float vitalityAmount, @Nullable LivingEntity attacker, @Nullable DamageSource source, boolean enableAttributeScaling, @Nullable String sourceMod, int priority) {
        return damagePoise(entity, poiseAmount, vitalityAmount, attacker, source, enableAttributeScaling, sourceMod, priority, null);
    }

    public static float damagePoise(LivingEntity entity, float poiseAmount, float vitalityAmount, @Nullable LivingEntity attacker, @Nullable DamageSource source, boolean enableAttributeScaling, @Nullable String sourceMod, int priority, @Nullable String ammoType) {
        if (entity == null || entity.level().isClientSide || (poiseAmount <= 0 && vitalityAmount <= 0)) return 0.0f;

        String attackId = PoiseDamageQueue.generateAttackId(entity, source, attacker);
        PoiseDamageQueue.PoiseDamageRequest req = new PoiseDamageQueue.PoiseDamageRequest(
                entity, poiseAmount, vitalityAmount, attacker, source, enableAttributeScaling, sourceMod != null ? sourceMod : "BaseDefense", priority, ammoType
        );
        PoiseDamageQueue.queueRequest(attackId, req);

        float estimatedPoise = poiseAmount;
        if (enableAttributeScaling && attacker != null && attacker.getAttributes().hasAttribute(ModAttributes.STRENGTH_DAMAGE_MULTIPLIER.get())) {
            estimatedPoise *= (float) attacker.getAttributeValue(ModAttributes.STRENGTH_DAMAGE_MULTIPLIER.get());
        }
        return estimatedPoise;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide) return;

        DamageSource source = event.getSource();
        if ("SkipStrengthDamage".equals(source.getMsgId()) || entity.getPersistentData().getBoolean("SkipStrengthDamage")) {
            if (com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)) {
                event.setCanceled(true);
            }
            return;
        }

        LivingEntity attacker = source.getEntity() instanceof LivingEntity livingAttacker ? livingAttacker : null;

        String attackId = PoiseDamageQueue.generateAttackId(entity, source, attacker);
        PoiseDamageQueue.PoiseDamageRequest winningRequest = PoiseDamageQueue.resolveWinningRequest(attackId);

        if (winningRequest != null) {
            executeDirectPoiseDamage(winningRequest, event);
        } else if (com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)) {
            // Fallback for unqueued, environmental, status effect, or mismatched attacks on bosses
            float rawDamage = event.getAmount();
            if (rawDamage > 0) {
                com.nhatbh.basedefensev2.boss.core.BossComponent comp = com.nhatbh.basedefensev2.boss.core.BossManager.get(entity);
                double armor = comp != null ? com.nhatbh.basedefensev2.boss.core.BossManager.calculateBossArmor(entity) : entity.getArmorValue();
                double effectiveArmor = armor;
                if (comp != null) effectiveArmor *= comp.getCorrosionMultiplier(armor);
                double apotheosisMult = com.nhatbh.basedefensev2.boss.core.BossManager.calculateApotheosisMultiplier(effectiveArmor);
                float preMitigated = (float) (rawDamage * apotheosisMult);

                PoiseDamageQueue.PoiseDamageRequest fallbackReq = new PoiseDamageQueue.PoiseDamageRequest(
                        entity, preMitigated, preMitigated, attacker, source, true, "BaseDefenseFallback", Priority.NORMAL
                );
                executeDirectPoiseDamage(fallbackReq, event);
            }
            event.setCanceled(true);
        }

        PoiseDamageQueue.clearOldRequests(entity.level().getGameTime());
    }

    public static float executeDirectPoiseDamage(PoiseDamageQueue.PoiseDamageRequest req) {
        return executeDirectPoiseDamage(req, null);
    }

    public static float executeDirectPoiseDamage(PoiseDamageQueue.PoiseDamageRequest req, @Nullable LivingHurtEvent hurtEvent) {
        LivingEntity entity = req.target;
        if (entity == null || entity.level().isClientSide) return 0.0f;

        if (com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)) {
            com.nhatbh.basedefensev2.boss.core.BossManager.recordBossCombatActivity(entity);
        }
        if (req.attacker != null && com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(req.attacker)) {
            com.nhatbh.basedefensev2.boss.core.BossManager.recordBossCombatActivity(req.attacker);
        }

        EntityStrengthData data = getPoiseData(entity);
        if (data == null) return 0.0f;

        float calculatedPoiseDamage = req.poiseAmount;
        float calculatedVitalityDamage = req.vitalityAmount;
        LivingEntity attacker = req.attacker;
        DamageSource source = req.source;
        String modTag = req.sourceMod;

        if (req.enableAttributeScaling) {
            if (attacker != null && attacker.getAttributes().hasAttribute(ModAttributes.STRENGTH_DAMAGE_MULTIPLIER.get())) {
                float mult = (float) attacker.getAttributeValue(ModAttributes.STRENGTH_DAMAGE_MULTIPLIER.get());
                calculatedPoiseDamage *= mult;
                calculatedVitalityDamage *= mult;
            }
            if (entity.getAttributes().hasAttribute(ModAttributes.STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get())) {
                float mult = (float) entity.getAttributeValue(ModAttributes.STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get());
                calculatedPoiseDamage *= mult;
                calculatedVitalityDamage *= mult;
            }
        }

        PoiseDamageEvent event = new PoiseDamageEvent(entity, attacker, source, calculatedPoiseDamage);
        if (MinecraftForge.EVENT_BUS.post(event)) return 0.0f;

        float finalPoiseDamage = event.getAmount();
        boolean wasExhaustedBefore = isExhausted(entity);

        if (!wasExhaustedBefore && finalPoiseDamage > 0 && data.currentStrength > 0) {
            data.currentStrength = Math.max(0.0f, data.currentStrength - finalPoiseDamage);
            if (data.currentStrength <= 0) {
                data.currentStrength = 0.0f;
                data.recoveryTicks = 300;
                triggerPoiseBreak(entity);
            }
            data.save(entity);
            EntityStrengthData.sync(entity, data);
        }

        if (calculatedVitalityDamage > 0) {
            if (com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)) {
                if (isExhausted(entity)) {
                    com.nhatbh.basedefensev2.boss.core.BossComponent comp = com.nhatbh.basedefensev2.boss.core.BossManager.get(entity);
                    if (comp != null) {
                        float finalVitDmg = calculatedVitalityDamage;
                        if (req.ammoType != null && !req.ammoType.isEmpty()) {
                            finalVitDmg = comp.getAdaptiveArmorTracker().processVitalityDamage(entity, req.ammoType, calculatedVitalityDamage, attacker);
                        }
                        double vitDmg = (double) finalVitDmg;
                        comp.getVitalityPool().damage(vitDmg);
                        comp.getVitalityPool().saveToNBT(entity.getPersistentData());
                        comp.getVitalityPool().syncToVanillaHealth(entity, source, attacker);
                        com.nhatbh.basedefensev2.boss.core.BossManager.syncBossVitality(entity, comp);
                        com.nhatbh.basedefensev2.boss.core.BossManager.checkPhaseTransition(entity, comp);
                    }
                }
                if (hurtEvent != null) {
                    hurtEvent.setCanceled(true);
                }
            } else {
                float finalHealthDmg = isExhausted(entity) ? calculatedVitalityDamage : calculateMitigatedDamage(entity, calculatedVitalityDamage);
                if (hurtEvent != null) {
                    hurtEvent.setAmount(finalHealthDmg);
                } else if (finalHealthDmg > 0) {
                    entity.getPersistentData().putBoolean("SkipStrengthDamage", true);
                    DamageSource dmgSource = (source != null) ? source : (attacker != null ? entity.damageSources().mobAttack(attacker) : entity.damageSources().generic());
                    entity.hurt(dmgSource, finalHealthDmg);
                }
            }
        } else if (com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity) && hurtEvent != null) {
            hurtEvent.setCanceled(true);
        }

        if (ENABLE_DEBUG_LOGGING && com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)) {
            com.nhatbh.basedefensev2.boss.core.BossComponent comp = com.nhatbh.basedefensev2.boss.core.BossManager.get(entity);
            if (comp != null) {
                float curPoise = getCurrentPoise(entity);
                float maxPoise = getMaxPoise(entity);
                boolean isExhaustedNow = isExhausted(entity);
                double curVit = comp.getVitalityPool().getCurrentVitality();
                double maxVit = comp.getVitalityPool().getMaxVitality();
                if (!wasExhaustedBefore && !isExhaustedNow) {
                    com.nhatbh.basedefensev2.boss.core.BossManager.debugChat(entity, String.format("§c[PoiseAPI] §e[%s] §b[Poise] §f-%.1f §7(%.1f/%.1f) §c[PROTECTED] §7| VitDmg: 0.0", modTag, finalPoiseDamage, curPoise, maxPoise));
                } else if (!wasExhaustedBefore && isExhaustedNow) {
                    com.nhatbh.basedefensev2.boss.core.BossManager.debugChat(entity, String.format("§c[PoiseAPI] §e[%s] §b[POISE BROKEN!] §e[EXHAUSTED] §8| §a[Vitality] §f-%.1f §7(%.1f/%.1f)", modTag, calculatedVitalityDamage, curVit, maxVit));
                } else {
                    com.nhatbh.basedefensev2.boss.core.BossManager.debugChat(entity, String.format("§c[PoiseAPI] §e[%s] §e[EXHAUSTED] §8| §a[Vitality] §f-%.1f §7(%.1f/%.1f)", modTag, calculatedVitalityDamage, curVit, maxVit));
                }
            }
        }
        return finalPoiseDamage;
    }

    public static float damagePoiseDirect(LivingEntity entity, float amount) {
        return damagePoise(entity, amount, amount, null, null, false);
    }

    /**
     * Calculates range-based poise damage multiplier between target and attacker.
     * Returns insideMultiplier if distance <= maxRange, or outsideMultiplier if distance > maxRange.
     */
    public static float getRangeBasedPoiseMultiplier(LivingEntity target, @Nullable LivingEntity attacker, float maxRange, float insideMultiplier, float outsideMultiplier) {
        if (attacker == null) return insideMultiplier;
        double distance = target.distanceTo(attacker);
        return (distance <= maxRange) ? insideMultiplier : outsideMultiplier;
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

        if (entity instanceof Mob mob && mob.getPersistentData().getBoolean("ExhaustionDisabledAI")) {
            mob.setNoAi(false);
            mob.getPersistentData().remove("ExhaustionDisabledAI");
        }

        if (com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)) {
            com.nhatbh.basedefensev2.boss.core.BossComponent comp = com.nhatbh.basedefensev2.boss.core.BossManager.get(entity);
            if (comp != null) {
                comp.resetCorrosionHits();
                comp.resetAdaptiveArmor(entity);
                com.nhatbh.basedefensev2.boss.core.BossManager.syncBossVitality(entity, comp);
            }
            MinecraftForge.EVENT_BUS.post(new com.nhatbh.basedefensev2.api.event.BossAdaptiveArmorEvent.Reset(entity));
        } else {
            entity.getPersistentData().remove("bdv2_corrosion_hits");
            entity.getPersistentData().remove("bdv2_corrosion_window_tick");
            entity.getPersistentData().remove("bdv2_corrosion_window_hits");
        }
        removeCorrosionAttributeModifier(entity);

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
        if (!entity.level().isClientSide) {
            if (com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)) {
                // Apply 5% max vitality true damage on poise break directly to Boss Vitality Pool
                com.nhatbh.basedefensev2.boss.core.BossComponent comp = com.nhatbh.basedefensev2.boss.core.BossManager.get(entity);
                if (comp != null) {
                    double vitDmg = comp.getVitalityPool().getMaxVitality() * 0.05;
                    comp.getVitalityPool().damage(vitDmg);
                    comp.getVitalityPool().saveToNBT(entity.getPersistentData());
                    comp.getVitalityPool().syncToVanillaHealth(entity);
                    com.nhatbh.basedefensev2.boss.core.BossManager.syncBossVitality(entity, comp);
                    com.nhatbh.basedefensev2.boss.core.BossManager.checkPhaseTransition(entity, comp);
                }
            } else {
                // Apply 5% max health true damage on poise break (exhaustion start) for non-boss mobs
                entity.getPersistentData().putBoolean("SkipStrengthDamage", true);
                entity.getPersistentData().putBoolean("BdV2TrueDamage", true);
                entity.hurt(entity.damageSources().fellOutOfWorld(), entity.getMaxHealth() * 0.05f);
            }
        }
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            if (!mob.onGround()) {
                mob.setDeltaMovement(0, -0.6, 0);
                mob.hasImpulse = true;
            } else {
                mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
            }
            if (mob.isNoAi() && mob.getPersistentData().getBoolean("ExhaustionDisabledAI")) {
                mob.setNoAi(false);
                mob.getPersistentData().remove("ExhaustionDisabledAI");
            }
        }
        MinecraftForge.EVENT_BUS.post(new EntityEvents.PoiseBroken(entity));
    }

    /**
     * Extracts the weapon/attack Impact score on hit.
     * Checks EpicFightDamageSource#calculateImpact() first, then attacker's IMPACT attribute, defaulting to 2.5f baseline.
     */
    public static float getImpactScore(@Nullable DamageSource source, @Nullable LivingEntity attacker) {
        if (source instanceof yesman.epicfight.world.damagesource.EpicFightDamageSource epicSource) {
            float calculatedImpact = epicSource.calculateImpact();
            if (calculatedImpact > 0.0f) {
                return calculatedImpact;
            }
        }

        if (attacker != null && attacker.getAttributes() != null) {
            var impactAttr = attacker.getAttribute(yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes.IMPACT.get());
            if (impactAttr != null) {
                float val = (float) impactAttr.getValue();
                if (val > 0.0f) {
                    return val;
                }
            }
        }

        return 2.5f;
    }

    /**
     * Calculates the poise damage multiplier based on weapon Impact score.
     * Baseline impact of 2.5 corresponds to 1.0x strength poise damage multiplier.
     */
    public static float getImpactPoiseDamageMultiplier(float impactScore) {
        return Math.max(0.1f, impactScore / 2.5f);
    }

    /**
     * Calculates the damage multiplier on poise break based on weapon Impact score.
     * Baseline impact of 2.5 corresponds to 1.0x strength damage multiplier.
     */
    public static float getPoiseBreakDamageMultiplier(float impactScore) {
        return Math.max(0.2f, impactScore / 2.5f);
    }
}
