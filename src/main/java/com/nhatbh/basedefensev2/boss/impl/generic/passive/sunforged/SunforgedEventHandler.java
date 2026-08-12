package com.nhatbh.basedefensev2.boss.impl.generic.passive.sunforged;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber
public class SunforgedEventHandler {

    private static final Map<LivingEntity, SunforgedBulwarkController> activeControllers = new HashMap<>();

    public static void registerController(LivingEntity boss, SunforgedBulwarkController controller) {
        activeControllers.put(boss, controller);
    }

    public static void unregisterController(LivingEntity boss) {
        activeControllers.remove(boss);
    }

    public static SunforgedBulwarkController getController(LivingEntity boss) {
        return activeControllers.get(boss);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        SunforgedBulwarkController controller = activeControllers.get(victim);
        if (controller != null) {
            controller.onBossDamaged(event);
        }
    }
}
