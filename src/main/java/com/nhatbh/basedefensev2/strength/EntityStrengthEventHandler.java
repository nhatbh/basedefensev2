package com.nhatbh.basedefensev2.strength;

import com.nhatbh.basedefensev2.api.PoiseAPI;
import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.core.BossComponent;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.utils.UUIDHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
                BossDefinition def = BossManager.get(living).getDefinition();
                float poiseScale = 1.0f;
                if (def.getBaseStats() != null && def.getBaseStats().health > 0) {
                    poiseScale = living.getMaxHealth() / def.getBaseStats().health;
                }
                float scaledPoise = def.getMaxPoise() * poiseScale;
                PoiseAPI.initializePoise(living, scaledPoise, def.getPoiseDamageReduction(), true);
            } else if (living instanceof Mob mob) {
                float maxHp = mob.getMaxHealth();
                float maxStrength = maxHp * 1.0f;
                float reductionValue = 4.0f + (maxHp * 0.05f);
                PoiseAPI.initializePoise(mob, maxStrength, reductionValue, false);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Disable all damage attributed to an exhausted attacker (melee, projectile, magic, etc.)
        if (event.getSource().getEntity() instanceof LivingEntity attacker && PoiseAPI.isExhausted(attacker)) {
            event.setCanceled(true);
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !PoiseAPI.hasPoise(entity))
            return;

        DamageSource source = event.getSource();
        if ("SkipStrengthDamage".equals(source.getMsgId())
                || entity.getPersistentData().getBoolean("SkipStrengthDamage")) {
            entity.getPersistentData().remove("SkipStrengthDamage");
            return;
        }

        if (!PoiseAPI.isExhausted(entity)) {
            float rawDamage = event.getAmount();
            float basePoiseDamage = source.isIndirect() ? rawDamage * 0.5f : rawDamage;
            LivingEntity attacker = source.getEntity() instanceof LivingEntity livingAttacker ? livingAttacker : null;

            PoiseAPI.damagePoise(entity, basePoiseDamage, attacker, source, true);

            event.setAmount(PoiseAPI.calculateMitigatedDamage(entity, rawDamage));
        }
    }

    private static final UUID EXHAUSTION_SPEED_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("poise_exhaustion", "movement_speed");
    private static final UUID EXHAUSTION_ATTACK_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("poise_exhaustion", "attack_damage");

    @SubscribeEvent
    public static void onLivingHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (PoiseAPI.isExhausted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
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
            data.recoveryTicks -= 1;
            data.save(entity);

            if (!entity.level().isClientSide && data.recoveryTicks <= 0) {
                PoiseAPI.resetPoise(entity);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (BossManager.isBoss(event.getEntity())) {
            // Damage handling relocated to BossManager
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity living) {
            PoiseAPI.syncPoise(living);
        }
    }
}
