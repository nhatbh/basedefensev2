package com.nhatbh.basedefensev2.boss.skills;

import net.minecraftforge.event.entity.living.LivingDamageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ActiveSequence {
    private final String id;
    private final List<Step> steps;

    private ActiveSequence(String id, List<Step> steps) {
        this.id = id;
        this.steps = steps;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String getId() {
        return id;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public SequenceRunner start(com.nhatbh.basedefensev2.boss.core.AbstractBossEntity boss) {
        return new SequenceRunner(this, boss);
    }

    public static class Step {
        public final String id;
        public final int duration;
        public final boolean isParry;
        
        public Consumer<SkillContext> onStart;
        public Consumer<SkillContext> onTick;
        public BiConsumer<SkillContext, LivingDamageEvent> onDamageTaken;
        public BiConsumer<SkillContext, LivingDamageEvent> onMeleeDamageTaken;
        
        public Step(String id, int duration, boolean isParry) {
            this.id = id;
            this.duration = duration;
            this.isParry = isParry;
        }
    }

    public static class Builder {
        private final String id;
        private final List<Step> steps = new ArrayList<>();
        private Step currentStep;

        public Builder(String id) {
            this.id = id;
        }

        public Builder step(String id, int duration) {
            finalizeStep();
            currentStep = new Step(id, duration, false);
            return this;
        }

        public Builder parryStep(String id, int duration) {
            finalizeStep();
            currentStep = new Step(id, duration, true);
            return this;
        }

        public Builder onStart(Consumer<SkillContext> handler) {
            if (currentStep != null) currentStep.onStart = handler;
            return this;
        }

        public Builder onTick(Consumer<SkillContext> handler) {
            if (currentStep != null) currentStep.onTick = handler;
            return this;
        }

        public Builder onDamageTaken(BiConsumer<SkillContext, LivingDamageEvent> handler) {
            if (currentStep != null) currentStep.onDamageTaken = handler;
            return this;
        }

        public Builder onMeleeDamageTaken(BiConsumer<SkillContext, LivingDamageEvent> handler) {
            if (currentStep != null) currentStep.onMeleeDamageTaken = handler;
            return this;
        }

        private void finalizeStep() {
            if (currentStep != null) {
                steps.add(currentStep);
                currentStep = null;
            }
        }

        public ActiveSequence build() {
            finalizeStep();
            return new ActiveSequence(id, steps);
        }
    }
}
