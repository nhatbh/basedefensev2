package com.nhatbh.basedefensev2.boss.impl.generic.passive.dragonsfury;

import com.nhatbh.basedefensev2.api.PoiseAPI;
import com.nhatbh.basedefensev2.api.event.PoiseDamageEvent;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.registry.ModEffects;
import com.nhatbh.basedefensev2.strength.EntityStrengthData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class DragonsFuryEventHandler {

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity living = event.getEntity();
        if (living == null)
            return;

        if (ModEffects.HEALING_BLOCK.isPresent() && living.hasEffect(ModEffects.HEALING_BLOCK.get())) {
            event.setCanceled(true);
            return;
        }

        if (ModEffects.SEARED.isPresent() && living.hasEffect(ModEffects.SEARED.get())) {
            event.setAmount(event.getAmount() * 0.5f);
        }
    }

    @SubscribeEvent
    public static void onPoiseDamage(PoiseDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide())
            return;

        if (!BossManager.isBoss(entity))
            return;

        DragonsFuryPassive fury = DragonsFuryPassive.get(entity);
        if (fury == null || !fury.isEnraged())
            return;

        LivingEntity attacker = event.getAttacker();
        float insideMult = fury.isDesperationActive() ? 2.0f : 1.5f;
        float mult = PoiseAPI.getRangeBasedPoiseMultiplier(entity, attacker, 6.0f, insideMult, 0.0f);

        if (mult <= 0.0f) {
            event.setCanceled(true);
        } else {
            event.setAmount(event.getAmount() * mult);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide())
            return;

        if (!BossManager.isBoss(entity))
            return;

        DragonsFuryPassive fury = DragonsFuryPassive.get(entity);
        if (fury == null)
            return;

        // 1. Process Rage Gain on Hit
        if (event.getSource().getEntity() instanceof Player player) {
            fury.onBossHit(player);
        } else if (event.getSource().getEntity() instanceof LivingEntity livingAttacker) {
            fury.onBossHit(livingAttacker);
        }

        // 2. Enraged Range Damage Rule:
        // When enraged: 0 strength damage outside 6m range; +50% (P1) or +100% (P2) damage inside 6m range.
        if (fury.isEnraged()) {
            net.minecraft.world.entity.Entity attacker = event.getSource().getEntity();
            if (attacker != null) {
                double distance = entity.distanceTo(attacker);
                if (distance > 6.0) {
                    event.setAmount(0.0f);
                    event.setCanceled(true);
                    return;
                } else {
                    float multiplier = fury.isDesperationActive() ? 2.0f : 1.5f;
                    event.setAmount(event.getAmount() * multiplier);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide())
            return;

        if (!BossManager.isBoss(entity))
            return;

        DragonsFuryPassive fury = DragonsFuryPassive.get(entity);
        if (fury == null)
            return;

        // Phase 2 Desperation Execute bonus when strength poise is broken
        if (fury.isDesperationActive()) {
            EntityStrengthData strengthData = EntityStrengthData.get(entity);
            if (strengthData == null || strengthData.currentStrength <= 0) {
                event.setAmount(event.getAmount() * 2.0f);
            }
        }
    }

    @SubscribeEvent
    public static void onPoiseBroken(com.nhatbh.basedefensev2.strength.EntityEvents.PoiseBroken event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide())
            return;

        if (BossManager.isBoss(entity)) {
            DragonsFuryPassive fury = DragonsFuryPassive.get(entity);
            if (fury != null) {
                fury.onShieldBreak();
            }
        }
    }
}
