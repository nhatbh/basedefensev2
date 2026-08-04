package com.nhatbh.basedefensev2.boss.impl.generic;

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
        return "Đại Nhật tụ khí, kim quang hộ thể. Phàm kẻ nào mang đao kiếm áp sát, tất bị chân hỏa phệ hồn. Tới hồi nhật luân tĩnh diệt, vạn trượng liệt hỏa sẽ giáng trần, thiêu tận bát phương.";
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
        SunforgedBulwarkController controller = CONTROLLERS.remove(boss);
        if (controller != null) {
            controller.getSentinelManager().clear();
        }
        SunforgedEventHandler.unregisterController(boss);
    }

    public static SunforgedBulwarkController getController(LivingEntity boss) {
        return CONTROLLERS.get(boss);
    }
}
