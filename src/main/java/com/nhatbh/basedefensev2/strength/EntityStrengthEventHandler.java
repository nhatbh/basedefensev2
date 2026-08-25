package com.nhatbh.basedefensev2.strength;

import com.nhatbh.basedefensev2.api.PoiseAPI;
import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.utils.UUIDHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class EntityStrengthEventHandler {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide)
            return;
        Entity entity = event.getEntity();

        if (entity instanceof LivingEntity living && !(living instanceof Player) && !(living instanceof ArmorStand) && !PoiseAPI.hasPoise(living)) {
            if (BossManager.isBoss(living)) {
                com.nhatbh.basedefensev2.boss.core.BossComponent comp = BossManager.get(living);
                double maxVitality = (comp != null) ? comp.getVitalityPool().getMaxVitality() : (double) living.getMaxHealth();
                float scaledPoise = PoiseAPI.calculateMobMaxPoise((float) maxVitality);
                float reduction = (comp != null && comp.getDefinition() != null) ? comp.getDefinition().getPoiseDamageReduction() : 0.95f;
                PoiseAPI.initializePoise(living, scaledPoise, reduction, true);
            } else if (living instanceof Mob mob) {
                float maxHp = mob.getMaxHealth();
                float maxStrength = PoiseAPI.calculateMobMaxPoise(maxHp);
                float reductionValue = 4.0f + (maxHp * 0.05f);
                PoiseAPI.initializePoise(mob, maxStrength, reductionValue, false);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        // Disable all damage attributed to an exhausted attacker (melee, projectile, magic, etc.)
        if (event.getSource().getEntity() instanceof LivingEntity attacker && PoiseAPI.isExhausted(attacker)) {
            event.setCanceled(true);
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide)
            return;

        DamageSource source = event.getSource();
        if ("SkipStrengthDamage".equals(source.getMsgId())
                || entity.getPersistentData().getBoolean("SkipStrengthDamage")) {
            entity.getPersistentData().remove("SkipStrengthDamage");
            if (BossManager.isBoss(entity)) {
                event.setCanceled(true);
            }
            return;
        }

        if (!PoiseAPI.hasPoise(entity)) {
            if (BossManager.isBoss(entity)) {
                float preMitigated = getPreMitigatedDamage(event, entity, null);
                PoiseAPI.damagePoise(entity, 0.0f, preMitigated, source.getEntity() instanceof LivingEntity livingAttacker ? livingAttacker : null, source, true);
                entity.level().broadcastEntityEvent(entity, (byte) 2);
                event.setCanceled(true);
            }
            return;
        }

        float[] rawOut = new float[1];
        float preMitigatedDamage = getPreMitigatedDamage(event, entity, rawOut);
        float rawDamage = rawOut[0];
        LivingEntity attacker = source.getEntity() instanceof LivingEntity livingAttacker ? livingAttacker : null;
        boolean isDirectMelee = !source.isIndirect() && !(source instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource);

        if (!BossManager.isBoss(entity) && event.getAmount() > 0) {
            long currentTick = entity.level().getGameTime();
            long windowStartTick = entity.getPersistentData().getLong("bdv2_corrosion_window_tick");
            int hitsInWindow = entity.getPersistentData().getInt("bdv2_corrosion_window_hits");

            if (windowStartTick == 0 || (currentTick - windowStartTick) >= 20) {
                windowStartTick = currentTick;
                hitsInWindow = 0;
            }

            if (hitsInWindow < 2) {
                entity.getPersistentData().putLong("bdv2_corrosion_window_tick", windowStartTick);
                entity.getPersistentData().putInt("bdv2_corrosion_window_hits", hitsInWindow + 1);

                int hits = entity.getPersistentData().getInt("bdv2_corrosion_hits");
                entity.getPersistentData().putInt("bdv2_corrosion_hits", hits + 1);
                PoiseAPI.applyCorrosionAttributeModifier(entity);
                if (PoiseAPI.isFullyCorroded(entity)) {
                    PoiseAPI.clearBeneficialEffects(entity);
                }
            }
        }

        float basePoiseDamage;
        float impactScore = 2.5f;

        if (isDirectMelee) {
            basePoiseDamage = preMitigatedDamage;
            impactScore = PoiseAPI.getImpactScore(source, attacker);
            float impactMult = PoiseAPI.getImpactPoiseDamageMultiplier(impactScore);
            basePoiseDamage *= impactMult;

            if (attacker != null && BossManager.isBoss(entity)) {
                float riposteMult = com.nhatbh.basedefensev2.effects.RiposteEffect.getPoiseDamageMultiplier(attacker);
                basePoiseDamage *= riposteMult;
            }
        } else {
            basePoiseDamage = preMitigatedDamage * 0.5f;
        }

        // Vitality damage is calculated from damage before mitigation (reduced by Apotheosis resistance) WITHOUT weapon impact mechanic
        float vitalityDamage = preMitigatedDamage;

        float actualPoiseDmg = PoiseAPI.damagePoise(entity, basePoiseDamage, vitalityDamage, attacker, source, true, "BaseDefense", PoiseAPI.Priority.NORMAL);

        if (BossManager.isBoss(entity)) {
            entity.level().broadcastEntityEvent(entity, (byte) 2);
        }
    }

    private static float getPreMitigatedDamage(LivingHurtEvent event, LivingEntity entity, float[] rawOut) {
        if (entity.getPersistentData().contains("bdv2_cached_pre_mitigated_dmg")) {
            double preMit = entity.getPersistentData().getDouble("bdv2_cached_pre_mitigated_dmg");
            double raw = entity.getPersistentData().getDouble("bdv2_cached_raw_dmg");
            entity.getPersistentData().remove("bdv2_cached_pre_mitigated_dmg");
            entity.getPersistentData().remove("bdv2_cached_raw_dmg");
            if (rawOut != null && rawOut.length > 0) rawOut[0] = (float) raw;
            return (float) preMit;
        }
        double armor = BossManager.isBoss(entity) ? BossManager.calculateBossArmor(entity) : entity.getArmorValue();
        double effectiveArmor = armor * PoiseAPI.getCorrosionMultiplier(entity);
        double apotheosisMult = BossManager.calculateApotheosisMultiplier(effectiveArmor);
        float raw = event.getAmount();
        if (rawOut != null && rawOut.length > 0) rawOut[0] = raw;
        return (float) (raw * apotheosisMult);
    }

    private static final UUID EXHAUSTION_SPEED_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("poise_exhaustion", "movement_speed");
    private static final UUID EXHAUSTION_ATTACK_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("poise_exhaustion", "attack_damage");

    @SubscribeEvent
    public static void onLivingHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (PoiseAPI.isExhausted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && PoiseAPI.isExhausted(attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        boolean isExhausted = PoiseAPI.isExhausted(entity);

        // Server-only AI & attribute modifier logic
        if (!entity.level().isClientSide) {
            var speedAttr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            var attackAttr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            var armorAttr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);

            if (isExhausted) {
                // Reduce movement speed and attack damage to 0 via AttributeModifier
                if (speedAttr != null && speedAttr.getModifier(EXHAUSTION_SPEED_MOD_UUID) == null) {
                    speedAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            EXHAUSTION_SPEED_MOD_UUID, "ExhaustionSpeedZero", -1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
                if (attackAttr != null && attackAttr.getModifier(EXHAUSTION_ATTACK_MOD_UUID) == null) {
                    attackAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            EXHAUSTION_ATTACK_MOD_UUID, "ExhaustionAttackZero", -1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
                }

                if (entity instanceof Mob mob) {
                    mob.getNavigation().stop();
                    if (!mob.onGround()) {
                        // Force flying / airborne mobs to drop straight down to the floor
                        double downwardY = Math.min(-0.6, mob.getDeltaMovement().y - 0.1);
                        mob.setDeltaMovement(0, downwardY, 0);
                        mob.hasImpulse = true;
                    } else {
                        mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
                    }

                    if (mob.isNoAi() && mob.getPersistentData().getBoolean("ExhaustionDisabledAI")) {
                        mob.setNoAi(false);
                        mob.getPersistentData().remove("ExhaustionDisabledAI");
                    }
                }
            } else {
                // Remove Exhaustion Attribute Modifiers when no longer exhausted
                if (speedAttr != null && speedAttr.getModifier(EXHAUSTION_SPEED_MOD_UUID) != null) {
                    speedAttr.removeModifier(EXHAUSTION_SPEED_MOD_UUID);
                }
                if (attackAttr != null && attackAttr.getModifier(EXHAUSTION_ATTACK_MOD_UUID) != null) {
                    attackAttr.removeModifier(EXHAUSTION_ATTACK_MOD_UUID);
                }
            }
        }

        // Decrement recoveryTicks on both server and client so UI timers count down smoothly
        EntityStrengthData data = PoiseAPI.getPoiseData(entity);
        if (data != null && data.currentStrength <= 0 && data.recoveryTicks > 0) {
            boolean isSuppressed = entity.hasEffect(com.nhatbh.basedefensev2.registry.ModEffects.SUPPRESSION.get());
            boolean shouldDecrement = !isSuppressed || (entity.tickCount % 3 != 0);

            if (shouldDecrement) {
                data.recoveryTicks -= 1;
                data.save(entity);
            }

            if (!entity.level().isClientSide && data.recoveryTicks <= 0) {
                PoiseAPI.resetPoise(entity);
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity living) {
            PoiseAPI.syncPoise(living);
        }
    }
}
