package com.nhatbh.basedefensev2.boss.skills;

import com.nhatbh.basedefensev2.boss.events.BossEvents;
import com.nhatbh.basedefensev2.boss.network.EntitySkillSyncPacket;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.elemental.MobElementService;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

import java.util.List;

public class SequenceRunner {
    private final ActiveSequence sequence;
    private final List<ActiveSequence.Step> steps;
    private final SkillContext context;
    private final ActiveSkill.Type type;
    private int currentStepIndex = 0;
    private int tickInStep = 0;
    private boolean running = true;
    private final java.util.function.Consumer<SequenceRunner> onFinished;

    public SequenceRunner(ActiveSequence sequence, LivingEntity boss, ActiveSkill.Type type) {
        this(sequence, boss, type, null);
    }

    public SequenceRunner(ActiveSequence sequence, LivingEntity boss, ActiveSkill.Type type,
            java.util.function.Consumer<SequenceRunner> onFinished) {
        this.sequence = sequence;
        this.steps = sequence.getSteps();
        this.context = new SkillContext(boss);
        this.type = type;
        this.onFinished = onFinished;

        if (!steps.isEmpty()) {
            startCurrentStep();
        } else {
            running = false;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public SkillContext getContext() {
        return context;
    }

    public String getSequenceId() {
        return sequence.getId();
    }

    public ActiveSkill.Type getType() {
        return type;
    }

    public String getCurrentStepId() {
        if (currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex).id;
        }
        return "";
    }

    public void forceNextStep() {
        if (running) {
            nextStep();
        }
    }

    private void syncToClients() {
        if (!running) {
            BossSkillData.clear(context.boss());
            SkillIndicatorData dummy = new SkillIndicatorData(context.boss().getId(), "", 0, 0,
                    ActiveSequence.CounterType.NORMAL, 0, 0, null, null, false);
            NetworkManager.sendToTracking(new EntitySkillSyncPacket(dummy, true), context.boss());
            return;
        }

        ActiveSequence.Step step = steps.get(currentStepIndex);
        SkillIndicatorData data = new SkillIndicatorData(
                context.boss().getId(),
                step.id,
                tickInStep,
                step.duration,
                step.counterType,
                step.counterWindowStart,
                step.counterWindowEnd,
                step.counterDirection,
                step.magicElement,
                step.isParry);

        // Save to NBT on server
        BossSkillData nbtData = new BossSkillData(
                data.stepId, data.tickInStep, data.totalDuration, data.counterType,
                data.counterWindowStart, data.counterWindowEnd, data.counterDirection,
                data.magicElement, data.isParry);
        nbtData.save(context.boss());

        NetworkManager.sendToTracking(new EntitySkillSyncPacket(data, false), context.boss());
    }

    public void tick(LivingEntity boss) {
        if (!running)
            return;

        ActiveSequence.Step step = steps.get(currentStepIndex);
        context.setTickInStep(tickInStep);

        if (step.onTick != null) {
            step.onTick.accept(context);
        }

        // Periodic sync to prevent drift, and sync when counter window starts
        if (tickInStep % 20 == 0 || (step.isCounterable() && tickInStep == step.counterWindowStart)) {
            syncToClients();
        }

        checkContextFlags();

        if (running) {
            tickInStep++;
            if (tickInStep >= step.duration) {
                nextStep();
            }
        }
    }

    public void onDamage(LivingDamageEvent event, boolean isMelee, float rawDamage) {
        if (!running)
            return;

        ActiveSequence.Step step = steps.get(currentStepIndex);

        // 1. Check for Counter Punishment (Too early)
        if (step.isCounterable() && tickInStep < step.counterWindowStart) {
            applyPunishment(event, step);
            return;
        }

        // 2. Check for Counter Accuracy (During window)
        if (step.isCounterable() && tickInStep >= step.counterWindowStart && tickInStep <= step.counterWindowEnd) {
            if (isValidCounter(event, isMelee, step)) {
                if (step.counterType == ActiveSequence.CounterType.MAGIC && step.magicThreshold > 0) {
                    float damage = rawDamage; // Use raw damage before strength
                    float currentAccumulated = (float) context.data().getOrDefault("magic_counter_damage", 0f);
                    currentAccumulated += damage;
                    context.data().put("magic_counter_damage", currentAccumulated);

                    if (currentAccumulated >= step.magicThreshold) {
                        triggerCounter(event, step);
                        context.data().remove("magic_counter_damage");
                    }
                    return;
                } else {
                    triggerCounter(event, step);
                    return;
                }
            } else {
                // For other counters (NORMAL, DIRECTIONAL), strictly direct melee damage for
                // success/punishment
                if (step.counterType != ActiveSequence.CounterType.MAGIC && isMelee) {
                    applyPunishment(event, step);
                    return;
                }

                // For MAGIC counter, we no longer punish or cancel on non-matching damage
                // We just let it fall through to standard damage handling
            }
        }

        // 3. Original Parry Logic (Simple damage negation if step.isParry is true)
        // This is separate from the new Counter system for backward compatibility
        if (isMelee && step.isParry) {
            context.interrupt();
            event.setAmount(0);
            event.setAmount(0);
            MinecraftForge.EVENT_BUS.post(new BossEvents.ParrySuccessful(context.boss()));
            return;
        }

        if (step.onDamageTaken != null) {
            step.onDamageTaken.accept(context, event);
        }
        if (isMelee && step.onMeleeDamageTaken != null) {
            step.onMeleeDamageTaken.accept(context, event);
        }

        checkContextFlags();
    }


    public void onGuard(com.complextalents.epicfight.event.EpicFightGuardEvent event) {
        if (!running)
            return;
        ActiveSequence.Step step = steps.get(currentStepIndex);

        net.minecraft.world.entity.LivingEntity player = event.getPlayer();
        if (player == null)
            return;

        // Interruptible Dash - Staggers the boss if parried (Count as a guard counter)
        if (step.isInterruptibleDash && event.isParry()) {
            context.stopSequence();

            if (step.onDashParried != null) {
                step.onDashParried.accept(context, event);
            }
        }
    }

    private boolean isValidCounter(LivingDamageEvent event, boolean isMelee, ActiveSequence.Step step) {
        if (step.counterType == ActiveSequence.CounterType.NORMAL && isMelee) {
            return true;
        }
        if (step.counterType == ActiveSequence.CounterType.DIRECTIONAL && isMelee) {
            Vec3 requiredDir = step.counterDirection;
            if (requiredDir == null) {
                requiredDir = (Vec3) context.data().get("counter_dir");
            }
            if (requiredDir == null)
                return true;

            if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                Vec3 attackDir = attacker.position().subtract(context.boss().position()).normalize();
                double dot = attackDir.dot(requiredDir);
                return dot > 0.5; // Roughly the same direction
            }
        }
        if (step.counterType == ActiveSequence.CounterType.MAGIC
                && event.getSource() instanceof SpellDamageSource spellSource) {

            ElementType bossElement = MobElementService.getElement(context.boss());
            ElementType requiredElement = getCounteringElement(bossElement);

            // Allow override from data if specifically set
            if (context.data().containsKey("counter_element")) {
                requiredElement = (ElementType) context.data().get("counter_element");
            }

            var spell = spellSource.spell();
            if (spell != null) {
                String schoolPath = spell.getSchoolType().getId().getPath();
                return schoolPath.equalsIgnoreCase(requiredElement.name());
            }
        }
        return false;
    }

    private boolean isArcane(ElementType element) {
        if (element == null)
            return false;
        return switch (element) {
            case HOLY, EVOCATION, ENDER, ELDRITCH, BLOOD -> true;
            default -> false;
        };
    }

    private ElementType getCounteringElement(ElementType bossElement) {
        // Arcane elements are countered by themselves (exact same element)
        if (isArcane(bossElement)) {
            return bossElement;
        }

        // Primal elements are countered by the opposite element in the cycle
        // Fire -> Nature -> Aqua -> Lightning -> Ice -> Fire
        // So counter is reverse: Fire <- Ice, Nature <- Fire, Aqua <- Nature, Lightning
        // <- Aqua, Ice <- Lightning
        if (bossElement == ElementType.FIRE)
            return ElementType.ICE;
        if (bossElement == ElementType.NATURE)
            return ElementType.FIRE;
        if (bossElement == ElementType.AQUA)
            return ElementType.NATURE;
        if (bossElement == ElementType.LIGHTNING)
            return ElementType.AQUA;
        if (bossElement == ElementType.ICE)
            return ElementType.LIGHTNING;

        // Fallback for PHYSICAL or others
        return bossElement;
    }

    private void triggerCounter(LivingDamageEvent event, ActiveSequence.Step step) {
        context.stopSequence();
        context.setAllSkillsCooldown(600); // 10 seconds (200 ticks) of no skills
        event.setAmount(event.getAmount() * 1.5f); // Bonus damage for counter

        // Totem effect (sound and particles)
        context.boss().level().broadcastEntityEvent(context.boss(), (byte) 35);

        // Immediately clear NBT and sync to stop indicator rendering
        BossSkillData.clear(context.boss());
        SkillIndicatorData dummy = new SkillIndicatorData(context.boss().getId(), "", 0, 0,
                ActiveSequence.CounterType.NORMAL, 0, 0, null, null, false);
        NetworkManager.sendToTracking(new EntitySkillSyncPacket(dummy, true), context.boss());

        if (step.onCountered != null) {
            step.onCountered.accept(context, event);
        }
    }

    private void applyPunishment(LivingDamageEvent event, ActiveSequence.Step step) {
        if (step.punishmentHandler != null) {
            step.punishmentHandler.accept(context, event);
        } else if (step.counterType == ActiveSequence.CounterType.NORMAL) {
            // Default punishment for NORMAL counter: Jump to next step and make it lethal
            context.data().put("is_lethal_retaliation", true);
            nextStep();
        }
        event.setAmount(0);
        event.setCanceled(true);
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
            // context.log("§bSequence " + sequence.getId() + " finished.§r");
            // context.log("Counter triggered! " + step.onCountered != null ? "With custom
            // handler" : "");
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
        syncToClients();
        checkContextFlags();
    }
}
