package com.nhatbh.basedefensev2.boss.skills;

import com.nhatbh.basedefensev2.boss.core.AbstractBossEntity;

public class SkillContext {
    private final AbstractBossEntity boss;
    private boolean interrupted = false;
    private String jumpToStepId = null;

    public SkillContext(AbstractBossEntity boss) {
        this.boss = boss;
    }

    public AbstractBossEntity boss() {
        return boss;
    }

    public void interrupt() {
        this.interrupted = true;
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
        boss.applyExhaustion(ticks);
    }
}
