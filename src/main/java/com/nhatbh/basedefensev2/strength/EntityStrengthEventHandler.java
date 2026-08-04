package com.nhatbh.basedefensev2.strength;

import com.nhatbh.basedefensev2.api.PoiseAPI;
import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class EntityStrengthEventHandler {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide)
            return;
        Entity entity = event.getEntity();

        if (entity instanceof LivingEntity living && !PoiseAPI.hasPoise(living)) {
            if (BossManager.isBoss(living)) {
                BossDefinition def = BossManager.get(living).getDefinition();
                float poiseScale = 1.0f;
                if (def.getBaseStats() != null && def.getBaseStats().health > 0) {
                    poiseScale = living.getMaxHealth() / def.getBaseStats().health;
                }
                float scaledPoise = def.getMaxPoise() * poiseScale;
                PoiseAPI.initializePoise(living, scaledPoise, def.getPoiseDamageReduction(), true);
            } else if (living instanceof Monster monster) {
                float maxHp = monster.getMaxHealth();
                float maxStrength = maxHp * 1.0f;
                float reductionValue = 4.0f + (maxHp * 0.05f);
                PoiseAPI.initializePoise(monster, maxStrength, reductionValue, false);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
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

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();

        EntityStrengthData data = PoiseAPI.getPoiseData(entity);
        if (data != null && data.currentStrength <= 0 && data.recoveryTicks > 0) {
            data.recoveryTicks -= 1;

            if (data.recoveryTicks <= 0) {
                PoiseAPI.resetPoise(entity);
            } else {
                data.save(entity);
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

