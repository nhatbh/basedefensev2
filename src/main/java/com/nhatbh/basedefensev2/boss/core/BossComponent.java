package com.nhatbh.basedefensev2.boss.core;

import com.nhatbh.basedefensev2.boss.skills.SequenceRunner;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

public class BossComponent {
    private final BossDefinition definition;
    private Phase currentPhase;
    private int currentPhaseIndex = 0;
    private net.minecraft.world.entity.Entity currentMount;
    private final java.util.Set<String> usedBasicSkills = new java.util.HashSet<>();
    private final java.util.Set<String> usedTacticalSkills = new java.util.HashSet<>();

    private SequenceRunner currentSequence;
    private int exhaustionTicks = 0;
    private int basicSkillCooldown = 0;
    private int tacticalSkillCooldown = 0;

    protected final Map<String, Integer> skillCooldowns = new HashMap<>();

    private final BossVitalityPool vitalityPool = new BossVitalityPool();
    private final AdaptiveArmorTracker adaptiveArmorTracker = new AdaptiveArmorTracker();
    private int corrosionHits = 0;

    public BossComponent(BossDefinition definition) {
        this.definition = definition;
    }

    public void initialize(LivingEntity boss) {
        if (boss != null && boss.getPersistentData() != null) {
            adaptiveArmorTracker.loadFromNBT(boss.getPersistentData());
        }

        if (definition != null && definition.getBaseStats() != null) {
            if (boss != null && boss.getPersistentData().contains(BossVitalityPool.NBT_MAX_VITALITY)) {
                vitalityPool.loadFromNBT(boss.getPersistentData());
            } else {
                vitalityPool.initialize(definition.getBaseStats().health);
                if (boss != null) {
                    vitalityPool.saveToNBT(boss.getPersistentData());
                }
            }
        }

        if (!definition.getPhases().isEmpty()) {
            this.currentPhase = definition.getPhases().get(0);
            this.currentPhase.onEnter(boss);
        }
        
        // Initialize starting cooldowns for all skills in all phases
        for (Phase phase : definition.getPhases()) {
            for (Phase.ActiveSkillEntry entry : phase.getActives()) {
                if (entry.skill.getStartingCooldown() > 0) {
                    setSkillCooldown(entry.skill.getId(), entry.skill.getStartingCooldown());
                }
            }
        }
    }

    public BossVitalityPool getVitalityPool() { return vitalityPool; }
    public AdaptiveArmorTracker getAdaptiveArmorTracker() { return adaptiveArmorTracker; }
    public int getCorrosionHits() { return corrosionHits; }
    public void setCorrosionHits(int hits) { this.corrosionHits = Math.max(0, hits); }
    public void incrementCorrosionHits() { this.corrosionHits++; }
    public void resetCorrosionHits() { this.corrosionHits = 0; }
    public void resetAdaptiveArmor(@javax.annotation.Nullable LivingEntity boss) {
        this.adaptiveArmorTracker.reset(boss != null ? boss.getPersistentData() : null);
    }
    public double getCorrosionMultiplier(double baseArmor) {
        if (corrosionHits <= 0) return 1.0;
        double requiredHits = 25.0 + (Math.max(0.0, baseArmor) * 0.45);
        double ratio = Math.min(1.0, (double) corrosionHits / requiredHits);
        double progress = ratio * ratio; // Accelerating curve: early hits strip less, later hits strip significantly more
        return Math.max(0.0, 1.0 - progress);
    }

    public double getCorrosionMultiplier() {
        return getCorrosionMultiplier(100.0);
    }

    public BossDefinition getDefinition() { return definition; }
    public Phase getCurrentPhase() { return currentPhase; }
    public int getCurrentPhaseIndex() { return currentPhaseIndex; }
    public SequenceRunner getCurrentSequence() { return currentSequence; }
    public int getExhaustionTicks() { return exhaustionTicks; }
    public net.minecraft.world.entity.Entity getCurrentMount() { return currentMount; }

    public java.util.Set<String> getUsedBasicSkills() { return usedBasicSkills; }
    public java.util.Set<String> getUsedTacticalSkills() { return usedTacticalSkills; }

    public void markSkillUsed(String skillId, com.nhatbh.basedefensev2.boss.skills.ActiveSkill.Type type) {
        if (type == com.nhatbh.basedefensev2.boss.skills.ActiveSkill.Type.BASIC) {
            usedBasicSkills.add(skillId);
        } else if (type == com.nhatbh.basedefensev2.boss.skills.ActiveSkill.Type.TACTICAL) {
            usedTacticalSkills.add(skillId);
        }
    }

    public void clearUsedSkills(com.nhatbh.basedefensev2.boss.skills.ActiveSkill.Type type) {
        if (type == com.nhatbh.basedefensev2.boss.skills.ActiveSkill.Type.BASIC) {
            usedBasicSkills.clear();
        } else if (type == com.nhatbh.basedefensev2.boss.skills.ActiveSkill.Type.TACTICAL) {
            usedTacticalSkills.clear();
        }
    }

    public void setCurrentMount(net.minecraft.world.entity.Entity mount) {
        this.currentMount = mount;
    }

    public int getSkillCooldown(String skillId) {
        return this.skillCooldowns.getOrDefault(skillId, 0);
    }

    public void setSkillCooldown(String skillId, int ticks) {
        this.skillCooldowns.put(skillId, ticks);
    }

    public boolean isSkillReady(String skillId) {
        return getSkillCooldown(skillId) <= 0;
    }
    
    public Map<String, Integer> getSkillCooldowns() {
        return skillCooldowns;
    }

    public void setExhaustionTicks(int ticks) {
        this.exhaustionTicks = ticks;
    }

    public int getBasicSkillCooldown() { return basicSkillCooldown; }
    public void setBasicSkillCooldown(int ticks) { this.basicSkillCooldown = ticks; }
    public int getTacticalSkillCooldown() { return tacticalSkillCooldown; }
    public void setTacticalSkillCooldown(int ticks) { this.tacticalSkillCooldown = ticks; }

    public void tickGlobalCooldowns() {
        if (basicSkillCooldown > 0) basicSkillCooldown--;
        if (tacticalSkillCooldown > 0) tacticalSkillCooldown--;
    }

    public void setCurrentSequence(SequenceRunner sequence) {
        this.currentSequence = sequence;
    }

    public void setCurrentPhaseIndex(int index) {
        this.currentPhaseIndex = index;
    }
    
    public void setCurrentPhase(Phase phase) {
        this.currentPhase = phase;
    }

    public void setAllSkillsCooldown(int ticks) {
        for (Phase phase : definition.getPhases()) {
            for (Phase.ActiveSkillEntry entry : phase.getActives()) {
                String skillId = entry.skill.getId();
                if (getSkillCooldown(skillId) <= 0) {
                    setSkillCooldown(skillId, ticks);
                }
            }
        }
    }
}
