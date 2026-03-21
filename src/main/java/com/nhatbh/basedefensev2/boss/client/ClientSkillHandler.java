package com.nhatbh.basedefensev2.boss.client;

import com.nhatbh.basedefensev2.boss.skills.SkillIndicatorData;
import java.util.HashMap;
import java.util.Map;

public class ClientSkillHandler {
    private static final Map<Integer, SkillIndicatorData> ACTIVE_SKILLS = new HashMap<>();

    public static void handleSync(SkillIndicatorData data, boolean clear) {
        if (net.minecraft.client.Minecraft.getInstance().level != null) {
            net.minecraft.world.entity.Entity entity = net.minecraft.client.Minecraft.getInstance().level.getEntity(data.entityId);
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                if (clear) {
                    com.nhatbh.basedefensev2.boss.skills.BossSkillData.clear(living);
                    ACTIVE_SKILLS.remove(data.entityId);
                } else {
                    com.nhatbh.basedefensev2.boss.skills.BossSkillData bossData = new com.nhatbh.basedefensev2.boss.skills.BossSkillData(
                        data.stepId, data.tickInStep, data.totalDuration, data.counterType,
                        data.counterWindowStart, data.counterWindowEnd, data.counterDirection,
                        data.magicElement, data.isParry
                    );
                    bossData.save(living);
                    ACTIVE_SKILLS.put(data.entityId, data);
                }
            }
        }
    }

    public static SkillIndicatorData getSkillData(int entityId) {
        return ACTIVE_SKILLS.get(entityId);
    }

    public static void tick() {
        ACTIVE_SKILLS.values().removeIf(data -> {
            data.tickInStep++;
            return data.tickInStep > data.totalDuration + 20; // Allow some buffer
        });
    }

    public static Iterable<SkillIndicatorData> getAllActiveSkills() {
        return ACTIVE_SKILLS.values();
    }
}
