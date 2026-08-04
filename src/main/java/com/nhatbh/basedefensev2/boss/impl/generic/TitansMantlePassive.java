package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.skills.PassiveSkill;
import net.minecraft.world.entity.LivingEntity;

import java.util.WeakHashMap;

public class TitansMantlePassive implements PassiveSkill {

    private static final WeakHashMap<LivingEntity, TitansMantleController> CONTROLLERS = new WeakHashMap<>();

    @Override
    public String getName() {
        return "Nham Giáp";
    }

    @Override
    public String getTitlePrefix() {
        return "Titanic";
    }

    @Override
    public String getDescription() {
        return "Uy áp địa tầng ngưng kết, hóa Nham Giáp kiên phong bất phá. Một khi thạch trận hoàn chỉnh, Cổ thần giáng gót, vạn dặm sơn hà tất băng hoại thành tro bụi.";
    }

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
