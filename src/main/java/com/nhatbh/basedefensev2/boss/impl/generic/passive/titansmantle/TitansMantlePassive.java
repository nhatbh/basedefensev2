package com.nhatbh.basedefensev2.boss.impl.generic.passive.titansmantle;

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
        return "Bao phủ bởi Nham Giáp kiên cố (giảm 75% sát thương). Giáp tự động Hóa Cứng 1.5 giây sau mỗi đòn đánh, phản ngược đạn ma pháp về đối thủ. Các đòn đánh nặng giãn cách sẽ phá vỡ giáp (khiến boss chịu +50% sát thương trong 8s). Khi chuyển giai đoạn, boss thi triển Địa Thần Cuồng Chấn trừng phạt tham đòn.";
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
