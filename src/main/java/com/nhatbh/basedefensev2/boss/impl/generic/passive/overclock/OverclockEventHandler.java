package com.nhatbh.basedefensev2.boss.impl.generic.passive.overclock;

import com.nhatbh.basedefensev2.api.event.PoiseDamageEvent;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class OverclockEventHandler {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide() || com.nhatbh.basedefensev2.api.PoiseAPI.isExhausted(entity))
            return;

        if (!BossManager.isBoss(entity))
            return;

        OverclockPassive overclock = OverclockPassive.get(entity);
        if (overclock == null)
            return;

        // Process Charge gain on hit
        if (event.getSource().getEntity() instanceof Player player) {
            overclock.addCharge(entity, 6.0f);
        } else if (event.getSource().getEntity() instanceof LivingEntity livingAttacker) {
            overclock.addCharge(entity, 6.0f);
        }
    }

    @SubscribeEvent
    public static void onPoiseDamage(PoiseDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide() || com.nhatbh.basedefensev2.api.PoiseAPI.isExhausted(entity))
            return;

        if (!BossManager.isBoss(entity))
            return;

        OverclockPassive overclock = OverclockPassive.get(entity);
        if (overclock == null || !overclock.isSupercharged())
            return;

        // Supercharged state: +30% Strength / Poise damage taken from ALL distances
        event.setAmount(event.getAmount() * 1.30f);
    }
}
