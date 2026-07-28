package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.registry.ModEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber
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
        TitansMantleController controller = activeControllers.get(victim);

        if (controller != null) {
            boolean isDirectMelee = event.getSource().getDirectEntity() instanceof Player
                    && !event.getSource().isIndirect();
            Player attacker = isDirectMelee ? (Player) event.getSource().getEntity() : null;

            if (attacker != null) {
                controller.onBossDamaged(attacker, event.getAmount());
            }

            // Shattered Mantle: +30% damage taken
            if (victim.hasEffect(ModEffects.SHATTERED_MANTLE.get())) {
                event.setAmount(event.getAmount() * 1.30f);
            }

            // Seismic Rupture (Shield State): 80% physical DR & Heavy Hit / Parry Draining
            if (victim.hasEffect(ModEffects.SEISMIC_RUPTURE.get())) {
                if (attacker != null) {
                    boolean isParry = false; // Warrior parry callback integration
                    controller.handleHeavyHitOrParry(attacker, event.getAmount(), isParry);
                }

                // 80% damage reduction during Rupture against standard damage
                if (!isDirectMelee) {
                    event.setAmount(event.getAmount() * 0.20f);
                }
            }
        }
    }
}
