package com.nhatbh.basedefensev2.boss.impl.generic.passive.sunforged;

import com.nhatbh.basedefensev2.boss.skills.PassiveSkill;
import net.minecraft.world.entity.LivingEntity;

import java.util.WeakHashMap;

public class SunforgedBulwarkPassive implements PassiveSkill {

    private static final WeakHashMap<LivingEntity, SunforgedBulwarkController> CONTROLLERS = new WeakHashMap<>();

    @Override
    public String getName() {
        return "Nhật Luân";
    }

    @Override
    public String getTitlePrefix() {
        return "Sunforged";
    }

    @Override
    public String getDescription() {
        return "Đại Nhật giương khiên, kim quang chiếu thế. Khi Nhật Luân bật mở, trùm bị khóa hướng nhìn và giảm tốc độ di chuyển. Kẻ nào dám nhìn trực diện từ phía trước sẽ bị Hóa Đá. Hãy vòng ra sau lưng hoặc mạn sườn trùm để tấn công!";
    }

    @Override
    public void onAdded(LivingEntity boss) {
        SunforgedBulwarkController controller = new SunforgedBulwarkController(boss);
        CONTROLLERS.put(boss, controller);
        SunforgedEventHandler.registerController(boss, controller);
    }

    @Override
    public void tick(LivingEntity boss) {
        SunforgedBulwarkController controller = CONTROLLERS.computeIfAbsent(boss, b -> {
            SunforgedBulwarkController ctrl = new SunforgedBulwarkController(b);
            SunforgedEventHandler.registerController(b, ctrl);
            return ctrl;
        });
        controller.tick();
    }

    @Override
    public void onRemoved(LivingEntity boss) {
        CONTROLLERS.remove(boss);
        SunforgedEventHandler.unregisterController(boss);
    }

    public static SunforgedBulwarkController getController(LivingEntity boss) {
        return CONTROLLERS.get(boss);
    }
}
