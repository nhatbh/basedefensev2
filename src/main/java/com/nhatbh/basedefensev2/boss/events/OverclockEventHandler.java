package com.nhatbh.basedefensev2.boss.events;

import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.boss.impl.generic.OverclockPassive;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.elemental.events.ElementalDamageEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class OverclockEventHandler {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide()) return;

        // 1. Boss landing hits on players -> +10 Charge
        if (event.getSource().getEntity() instanceof LivingEntity attacker && BossManager.isBoss(attacker)) {
            if (event.getEntity() instanceof Player) {
                OverclockPassive overclock = OverclockPassive.get(attacker);
                if (overclock != null) {
                    overclock.addCharge(attacker, 10.0f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onElementalDamage(ElementalDamageEvent event) {
        LivingEntity target = event.getTarget();
        if (target == null || target.level().isClientSide()) return;

        if (!BossManager.isBoss(target)) return;

        OverclockPassive overclock = OverclockPassive.get(target);
        if (overclock == null || !overclock.isSupercharged()) return;

        ElementType element = event.getElement();
        if (element == ElementType.AQUA) {
            overclock.triggerConductionSurge(target);
        } else if (element == ElementType.ICE) {
            overclock.triggerThermalFriction(target);
        }
    }

    @SubscribeEvent
    public static void onSpellDamage(io.redspace.ironsspellbooks.api.events.SpellDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target == null || target.level().isClientSide()) return;

        if (!BossManager.isBoss(target)) return;

        OverclockPassive overclock = OverclockPassive.get(target);
        if (overclock == null || !overclock.isSupercharged()) return;

        var spellDamageSource = event.getSpellDamageSource();
        if (spellDamageSource == null || spellDamageSource.spell() == null) return;

        String schoolPath = spellDamageSource.spell().getSchoolType().getId().getPath();

        if ("ice".equals(schoolPath)) {
            overclock.triggerThermalFriction(target);
        } else if ("aqua".equals(schoolPath) || "water".equals(schoolPath)) {
            overclock.triggerConductionSurge(target);
        }
    }
}
