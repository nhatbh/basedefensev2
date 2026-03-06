package com.nhatbh.basedefensev2.boss.core;

import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;

public class SkillEvaluator {
    
    public static ActiveSkill getHighestPrioritySkill(AbstractBossEntity boss, Phase currentPhase) {
        if (currentPhase == null) return null;

        ActiveSkill bestSkill = null;
        int highestPriority = -1;

        for (Phase.ActiveSkillEntry entry : currentPhase.getActives()) {
            if (!entry.skill.isReady()) {
                continue;
            }

            int priority = entry.priorityFunction.apply(boss);
            if (priority > highestPriority) {
                highestPriority = priority;
                bestSkill = entry.skill;
            }
        }

        return highestPriority > 0 ? bestSkill : null;
    }
}
