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

    private SequenceRunner currentSequence;
    private int exhaustionTicks = 0;

    protected final Map<String, Integer> skillCooldowns = new HashMap<>();

    public BossComponent(BossDefinition definition) {
        this.definition = definition;
    }

    public void initialize(LivingEntity boss) {
        if (!definition.getPhases().isEmpty()) {
            this.currentPhase = definition.getPhases().get(0);
            this.currentPhase.onEnter(boss);
        }
    }

    public BossDefinition getDefinition() { return definition; }
    public Phase getCurrentPhase() { return currentPhase; }
    public int getCurrentPhaseIndex() { return currentPhaseIndex; }
    public SequenceRunner getCurrentSequence() { return currentSequence; }
    public int getExhaustionTicks() { return exhaustionTicks; }
    public net.minecraft.world.entity.Entity getCurrentMount() { return currentMount; }

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
