package com.nhatbh.basedefensev2.boss.impl.generic.passive.titansmantle;

import com.nhatbh.basedefensev2.boss.events.BossEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class TitansMantleEventHandler {

    private static final Map<LivingEntity, TitansMantleController> activeControllers = new HashMap<>();

    public static void registerController(LivingEntity boss, TitansMantleController controller) {
        activeControllers.put(boss, controller);
    }

    public static void unregisterController(LivingEntity boss) {
        activeControllers.remove(boss);
    }

    public static TitansMantleController getController(LivingEntity boss) {
        return activeControllers.get(boss);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide() || com.nhatbh.basedefensev2.api.PoiseAPI.isExhausted(victim)) return;

        TitansMantleController controller = activeControllers.get(victim);
        if (controller != null) {
            controller.onBossDamaged(event);
        }
    }

    @SubscribeEvent
    public static void onPhaseAdvance(BossEvents.PhaseAdvance event) {
        LivingEntity boss = event.getBoss();
        if (boss == null || boss.level().isClientSide()) return;

        TitansMantleController controller = activeControllers.get(boss);
        if (controller != null) {
            controller.triggerGroundSlam();
        }
    }
}
