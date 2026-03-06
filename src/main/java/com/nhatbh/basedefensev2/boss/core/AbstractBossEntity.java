package com.nhatbh.basedefensev2.boss.core;

import com.nhatbh.basedefensev2.boss.events.BossEvents;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.boss.skills.SequenceRunner;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

import java.util.List;

public abstract class AbstractBossEntity extends Zombie {
    private final BossDefinition definition;
    private Phase currentPhase;
    private int currentPhaseIndex = 0;
    
    private SequenceRunner currentSequence;
    private int exhaustionTicks = 0;

    protected AbstractBossEntity(EntityType<? extends Zombie> entityType, Level level, BossDefinition definition) {
        super(entityType, level);
        this.definition = definition;
        
        List<Phase> phases = definition.getPhases();
        if (!phases.isEmpty()) {
            this.currentPhase = phases.get(0);
            this.currentPhase.onEnter(this);
        }
    }

    public BossDefinition getDefinition() { return definition; }
    public Phase getCurrentPhase() { return currentPhase; }

    public void onPoiseBroken() {
        if (this.currentSequence != null) {
            this.currentSequence = null; // Interrupt sequence
        }
        this.applyExhaustion(100); // stunned
    }

    public void applyExhaustion(int ticks) {
        this.exhaustionTicks += ticks;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        if (exhaustionTicks > 0) {
            exhaustionTicks--;
            return;
        }

        checkPhaseTransition();

        if (currentPhase != null) {
            currentPhase.tickPassives(this);
            currentPhase.tickCooldowns();
        }

        if (this.currentSequence != null && this.currentSequence.isRunning()) {
            this.currentSequence.tick(this);
        } else {
            this.currentSequence = null;
            ActiveSkill nextSkill = SkillEvaluator.getHighestPrioritySkill(this, this.currentPhase);
            if (nextSkill != null) {
                nextSkill.startCooldown();
                this.currentSequence = nextSkill.getSequence().start(this);
            }
        }
    }

    private void checkPhaseTransition() {
        if (definition.getPhases().isEmpty()) return;
        
        float hpPercent = this.getHealth() / this.getMaxHealth();
        
        for (int i = 0; i < definition.getPhases().size(); i++) {
            Phase phase = definition.getPhases().get(i);
            // Phases are sorted descending by hp threshold.
            // When HP drops below a phase's threshold, it transitions.
            if (i > currentPhaseIndex && hpPercent <= phase.getHpThreshold()) {
                transitionToPhase(i, phase);
                break;
            }
        }
    }

    private void transitionToPhase(int newIndex, Phase newPhase) {
        if (currentPhase != null) {
            currentPhase.onExit(this);
        }
        
        int oldPhaseId = currentPhase != null ? currentPhase.getId() : -1;
        this.currentPhaseIndex = newIndex;
        this.currentPhase = newPhase;
        
        MinecraftForge.EVENT_BUS.post(new BossEvents.PhaseAdvance(this, oldPhaseId, newPhase.getId()));
        this.currentSequence = null; // Interrupted by phase shift
        
        newPhase.onEnter(this);
    }

    public void handleSequenceDamage(LivingDamageEvent event) {
        if (this.currentSequence != null && this.currentSequence.isRunning()) {
            boolean isMelee = !event.getSource().isIndirect();
            this.currentSequence.onDamage(event, isMelee);
        }
    }
}
