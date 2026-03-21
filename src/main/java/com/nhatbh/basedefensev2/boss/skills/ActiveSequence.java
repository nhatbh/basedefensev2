package com.nhatbh.basedefensev2.boss.skills;
import com.nhatbh.basedefensev2.elemental.ElementType;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.event.entity.living.LivingDamageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

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

    public SequenceRunner start(net.minecraft.world.entity.LivingEntity boss) {
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

        // Counter System
        public CounterType counterType = CounterType.NONE;
        public int counterWindowStart;
        public int counterWindowEnd;
        public BiConsumer<SkillContext, LivingDamageEvent> punishmentHandler;
        public Vec3 counterDirection;
        public ElementType magicElement;
        public BiConsumer<SkillContext, LivingDamageEvent> onCountered;
        
        public Step(String id, int duration, boolean isParry) {
            this.id = id;
            this.duration = duration;
            this.isParry = isParry;
        }

        public boolean isCounterable() {
            return counterType != CounterType.NONE;
        }
    }

    public enum CounterType {
        NONE, NORMAL, DIRECTIONAL, MAGIC
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

        public Builder counter(CounterType type, int start, int end) {
            if (currentStep != null) {
                currentStep.counterType = type;
                currentStep.counterWindowStart = start;
                currentStep.counterWindowEnd = end;
            }
            return this;
        }

        public Builder directional(Vec3 dir) {
            if (currentStep != null) {
                currentStep.counterType = CounterType.DIRECTIONAL;
                currentStep.counterDirection = dir;
            }
            return this;
        }

        public Builder magic(ElementType element) {
            if (currentStep != null) {
                currentStep.counterType = CounterType.MAGIC;
                currentStep.magicElement = element;
            }
            return this;
        }

        public Builder punishment(float damage) {
            if (currentStep != null) {
                currentStep.punishmentHandler = (ctx, event) -> {
                    if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                        attacker.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);
                    }
                };
            }
            return this;
        }

        public Builder punishment(BiConsumer<SkillContext, LivingDamageEvent> handler) {
            if (currentStep != null) currentStep.punishmentHandler = handler;
            return this;
        }

        public Builder onCountered(BiConsumer<SkillContext, LivingDamageEvent> handler) {
            if (currentStep != null) currentStep.onCountered = handler;
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
