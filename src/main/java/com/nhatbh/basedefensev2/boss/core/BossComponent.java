package com.nhatbh.basedefensev2.boss.core;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.skills.SequenceRunner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;

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

    public BossComponent(BossDefinition definition) {
        this.definition = definition;
    }

    public void initialize(LivingEntity boss) {
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
