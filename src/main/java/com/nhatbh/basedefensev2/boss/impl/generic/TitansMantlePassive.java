package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.skills.PassiveSkill;
import net.minecraft.world.entity.LivingEntity;

import java.util.WeakHashMap;

public class TitansMantlePassive implements PassiveSkill {

    private static final WeakHashMap<LivingEntity, TitansMantleController> CONTROLLERS = new WeakHashMap<>();

    @Override
    public void onAdded(LivingEntity boss) {
        TitansMantleController controller = new TitansMantleController(boss);
        CONTROLLERS.put(boss, controller);
        TitansMantleEventHandler.registerController(boss, controller);
    }

    @Override
    public void tick(LivingEntity boss) {
        TitansMantleController controller = CONTROLLERS.computeIfAbsent(boss, b -> {
            TitansMantleController ctrl = new TitansMantleController(b);
            TitansMantleEventHandler.registerController(b, ctrl);
            return ctrl;
        });
        controller.tick();
    }

    @Override
    public void onRemoved(LivingEntity boss) {
        TitansMantleController controller = CONTROLLERS.remove(boss);
        if (controller != null) {
            controller.clear();
        }
        TitansMantleEventHandler.unregisterController(boss);
    }

    public static TitansMantleController getController(LivingEntity boss) {
        return CONTROLLERS.get(boss);
    }
}
