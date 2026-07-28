package com.nhatbh.basedefensev2.boss.events;

import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.boss.impl.generic.DragonsFuryPassive;
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
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide())
            return;

        if (!BossManager.isBoss(entity))
            return;

        DragonsFuryPassive fury = DragonsFuryPassive.get(entity);
        if (fury == null)
            return;

        // 1. Process Rage Gain (0.25s per-player ICD)
        if (event.getSource().getEntity() instanceof Player player) {
            fury.onBossHit(player);
        } else if (event.getSource().getEntity() instanceof LivingEntity livingAttacker) {
            fury.onBossHit(livingAttacker);
        }

        // 2. Enraged Shield Rule is fully handled by STRENGTH_DAMAGE_TAKEN_MULTIPLIER
        // attribute in EntityStrengthEventHandler
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

        // 3. Phase 4 Execute Phase (200% HP damage taken when shield is broken)
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
