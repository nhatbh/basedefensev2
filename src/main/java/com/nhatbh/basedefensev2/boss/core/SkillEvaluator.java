package com.nhatbh.basedefensev2.boss.core;


import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;

public class SkillEvaluator {
    
    public static ActiveSkill selectSkill(BossComponent comp, net.minecraft.world.entity.LivingEntity boss, Phase currentPhase) {
        if (currentPhase == null) return null;

        java.util.List<WeightedSkill> candidates = new java.util.ArrayList<>();
        double totalWeight = 0;

        for (Phase.ActiveSkillEntry entry : currentPhase.getActives()) {
            if (!comp.isSkillReady(entry.skill.getId())) {
                continue;
            }

            if (entry.skill.getType() == ActiveSkill.Type.BASIC && comp.getBasicSkillCooldown() > 0) {
                continue;
            }

            if (entry.skill.getType() == ActiveSkill.Type.TACTICAL && comp.getTacticalSkillCooldown() > 0) {
                continue;
            }

            double priority = entry.priorityFunction.apply(boss);
            if (priority <= 0) continue;

            // Apply weight penalty if already used in this cycle
            java.util.Set<String> used = (entry.skill.getType() == ActiveSkill.Type.BASIC) 
                    ? comp.getUsedBasicSkills() : comp.getUsedTacticalSkills();
            
            if (used.contains(entry.skill.getId())) {
                priority *= 0.4; // 40% weight if used (60% penalty)
            }

            candidates.add(new WeightedSkill(entry.skill, priority));
            totalWeight += priority;
        }

        if (candidates.isEmpty()) return null;

        double randomValue = boss.getRandom().nextDouble() * totalWeight;
        double currentSum = 0;
        for (WeightedSkill ws : candidates) {
            currentSum += ws.weight;
            if (randomValue <= currentSum) {
                return ws.skill;
            }
        }

        return candidates.get(candidates.size() - 1).skill;
    }

    private static class WeightedSkill {
        final ActiveSkill skill;
        final double weight;

        WeightedSkill(ActiveSkill skill, double weight) {
            this.skill = skill;
            this.weight = weight;
        }
    }
}
