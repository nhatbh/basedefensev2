package com.nhatbh.basedefensev2.boss.skills;

import com.nhatbh.basedefensev2.boss.core.BossComponent;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

public class SkillContext {
    private final LivingEntity boss;
    private boolean interrupted = false;
    private final Map<String, Object> data = new HashMap<>();
    private String jumpToStepId = null;
    private int tickInStep = 0;

    public SkillContext(LivingEntity boss) {
        this.boss = boss;
    }

    public LivingEntity boss() {
        return boss;
    }

    public void interrupt() {
        this.interrupted = true;
    }

    public void stopSequence() {
        this.interrupted = true;
        BossComponent comp = BossManager.get(boss);
        if (comp != null) {
            comp.setCurrentSequence(null);
        }
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public void jumpToStep(String stepId) {
        this.jumpToStepId = stepId;
    }

    public String consumeJumpTarget() {
        String target = this.jumpToStepId;
        this.jumpToStepId = null; // consume it
        return target;
    }

    public void applyExhaustion(int ticks) {
        BossComponent comp = BossManager.get(boss);
        if (comp != null) {
            comp.setExhaustionTicks(comp.getExhaustionTicks() + ticks);
        }
    }

    public void setAllSkillsCooldown(int ticks) {
        BossComponent comp = BossManager.get(boss);
        if (comp != null) {
            comp.setAllSkillsCooldown(ticks);
        }
    }

    public void log(String message) {
        // Disabled log spam as requested
    }
    
    public double getHealthPercent() {
        return boss.getHealth() / boss.getMaxHealth();
    }

    public Map<String, Object> data() {
        return data;
    }

    public int getTicks() {
        return tickInStep;
    }

    public void setTickInStep(int tick) {
        this.tickInStep = tick;
    }

    public float getStrength() {
        com.nhatbh.basedefensev2.strength.EntityStrengthData data = com.nhatbh.basedefensev2.strength.EntityStrengthData.get(boss);
        return data != null ? data.currentStrength : 0f;
    }

    public float getMaxStrength() {
        com.nhatbh.basedefensev2.strength.EntityStrengthData data = com.nhatbh.basedefensev2.strength.EntityStrengthData.get(boss);
        return data != null ? data.maxStrength : 0f;
    }

    public void applyStrengthDamage(float amount) {
        com.nhatbh.basedefensev2.strength.EntityStrengthData data = com.nhatbh.basedefensev2.strength.EntityStrengthData.get(boss);
        if (data != null && data.currentStrength > 0) {
            data.currentStrength -= amount;
            if (data.currentStrength <= 0) {
                data.currentStrength = 0;
                data.recoveryTicks = 300;
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.nhatbh.basedefensev2.strength.EntityEvents.PoiseBroken(boss));
            }
            data.save(boss);
            com.nhatbh.basedefensev2.strength.EntityStrengthData.sync(boss, data);
        }
    }
}
