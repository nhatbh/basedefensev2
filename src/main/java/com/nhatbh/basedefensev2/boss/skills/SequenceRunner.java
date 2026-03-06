package com.nhatbh.basedefensev2.boss.skills;

import com.nhatbh.basedefensev2.boss.core.AbstractBossEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

import java.util.List;

public class SequenceRunner {
    private final ActiveSequence sequence;
    private final List<ActiveSequence.Step> steps;
    private final SkillContext context;
    private int currentStepIndex = 0;
    private int tickInStep = 0;
    private boolean running = true;

    public SequenceRunner(ActiveSequence sequence, AbstractBossEntity boss) {
        this.sequence = sequence;
        this.steps = sequence.getSteps();
        this.context = new SkillContext(boss);
        
        if (!steps.isEmpty()) {
            startCurrentStep();
        } else {
            running = false;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void tick(AbstractBossEntity boss) {
        if (!running) return;

        ActiveSequence.Step step = steps.get(currentStepIndex);
        
        if (step.onTick != null) {
            step.onTick.accept(context);
        }

        checkContextFlags();

        if (running) {
            tickInStep++;
            if (tickInStep >= step.duration) {
                nextStep();
            }
        }
    }

    public void onDamage(LivingDamageEvent event, boolean isMelee) {
        if (!running) return;
        
        ActiveSequence.Step step = steps.get(currentStepIndex);
        if (step.onDamageTaken != null) {
            step.onDamageTaken.accept(context, event);
        }
        if (isMelee && step.onMeleeDamageTaken != null) {
            step.onMeleeDamageTaken.accept(context, event);
        }
        
        checkContextFlags();
    }

    private void checkContextFlags() {
        if (context.isInterrupted()) {
            running = false;
        } else {
            String target = context.consumeJumpTarget();
            if (target != null) {
                jumpTo(target);
            }
        }
    }

    private void jumpTo(String stepId) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).id.equals(stepId)) {
                currentStepIndex = i;
                tickInStep = 0;
                startCurrentStep();
                return;
            }
        }
        // If step not found, stop
        running = false;
    }

    private void nextStep() {
        currentStepIndex++;
        tickInStep = 0;
        if (currentStepIndex >= steps.size()) {
            running = false;
        } else {
            startCurrentStep();
        }
    }

    private void startCurrentStep() {
        ActiveSequence.Step step = steps.get(currentStepIndex);
        if (step.onStart != null) {
            step.onStart.accept(context);
        }
        checkContextFlags();
    }
}
