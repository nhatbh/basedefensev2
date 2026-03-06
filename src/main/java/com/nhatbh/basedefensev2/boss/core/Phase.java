package com.nhatbh.basedefensev2.boss.core;

import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.boss.skills.PassiveSkill;
import com.nhatbh.basedefensev2.boss.skills.SkillContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class Phase {
    private final int id;
    private final float hpThreshold;
    private final List<PassiveSkill> passives;
    private final List<ActiveSkillEntry> actives;
    private final Consumer<SkillContext> onTransition;

    public Phase(int id, float hpThreshold, List<PassiveSkill> passives, List<ActiveSkillEntry> actives, Consumer<SkillContext> onTransition) {
        this.id = id;
        this.hpThreshold = hpThreshold;
        this.passives = passives;
        this.actives = actives;
        this.onTransition = onTransition;
    }

    public int getId() { return id; }
    public float getHpThreshold() { return hpThreshold; }
    public List<PassiveSkill> getPassives() { return passives; }
    public List<ActiveSkillEntry> getActives() { return actives; }

    public void onEnter(AbstractBossEntity boss) {
        if (onTransition != null) {
            onTransition.accept(new SkillContext(boss));
        }
        for (PassiveSkill passive : passives) {
            passive.onAdded(boss);
        }
    }

    public void onExit(AbstractBossEntity boss) {
        for (PassiveSkill passive : passives) {
            passive.onRemoved(boss);
        }
    }

    public void tickPassives(AbstractBossEntity boss) {
        for (PassiveSkill passive : passives) {
            passive.tick(boss);
        }
    }

    public void tickCooldowns() {
        for (ActiveSkillEntry entry : actives) {
            entry.skill.tickCooldown();
        }
    }

    public static class ActiveSkillEntry {
        public final ActiveSkill skill;
        public final Function<AbstractBossEntity, Integer> priorityFunction;

        public ActiveSkillEntry(ActiveSkill skill, Function<AbstractBossEntity, Integer> priorityFunction) {
            this.skill = skill;
            this.priorityFunction = priorityFunction;
        }
    }

    public static class Builder {
        private final int id;
        private float hpThreshold = 0f;
        private final List<PassiveSkill> passives = new ArrayList<>();
        private final List<ActiveSkillEntry> actives = new ArrayList<>();
        private Consumer<SkillContext> onTransition = null;

        public Builder(int id) {
            this.id = id;
        }

        public Builder hpThreshold(float threshold) {
            this.hpThreshold = threshold;
            return this;
        }

        public Builder addPassive(PassiveSkill passive) {
            this.passives.add(passive);
            return this;
        }

        public Builder addActive(ActiveSkill skill, Function<AbstractBossEntity, Integer> priorityCondition) {
            this.actives.add(new ActiveSkillEntry(skill, priorityCondition));
            return this;
        }

        public Builder onTransition(Consumer<SkillContext> handler) {
            this.onTransition = handler;
            return this;
        }

        public Phase build() {
            return new Phase(id, hpThreshold, passives, actives, onTransition);
        }
    }
}
