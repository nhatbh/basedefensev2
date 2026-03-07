package com.nhatbh.basedefensev2.boss.core;

import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.boss.skills.PassiveSkill;
import com.nhatbh.basedefensev2.boss.skills.SkillContext;

import net.minecraft.world.entity.LivingEntity;
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
    private final String mountEntity;
    private final String mainhandWeapon;

    public Phase(int id, float hpThreshold, List<PassiveSkill> passives, List<ActiveSkillEntry> actives, Consumer<SkillContext> onTransition, String mountEntity, String mainhandWeapon) {
        this.id = id;
        this.hpThreshold = hpThreshold;
        this.passives = passives;
        this.actives = actives;
        this.onTransition = onTransition;
        this.mountEntity = mountEntity;
        this.mainhandWeapon = mainhandWeapon;
    }
    public int getId() { return id; }
    public float getHpThreshold() { return hpThreshold; }
    public List<PassiveSkill> getPassives() { return passives; }
    public List<ActiveSkillEntry> getActives() { return actives; }
    public String getMountEntity() { return mountEntity; }
    public String getMainhandWeapon() { return mainhandWeapon; }

    public void onEnter(LivingEntity boss) {
        if (onTransition != null) {
            onTransition.accept(new SkillContext(boss));
        }
        for (PassiveSkill passive : passives) {
            passive.onAdded(boss);
        }
    }

    public void onExit(LivingEntity boss) {
        for (PassiveSkill passive : passives) {
            passive.onRemoved(boss);
        }
    }

    public void tickPassives(LivingEntity boss) {
        for (PassiveSkill passive : passives) {
            passive.tick(boss);
        }
    }

    public static class ActiveSkillEntry {
        public final ActiveSkill skill;
        public final Function<LivingEntity, Integer> priorityFunction;

        public ActiveSkillEntry(ActiveSkill skill, Function<LivingEntity, Integer> priorityFunction) {
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
        private String mountEntity = null;
        private String mainhandWeapon = null;

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

        public Builder addActive(ActiveSkill skill, Function<LivingEntity, Integer> priorityCondition) {
            this.actives.add(new ActiveSkillEntry(skill, priorityCondition));
            return this;
        }

        public Builder onTransition(Consumer<SkillContext> handler) {
            this.onTransition = handler;
            return this;
        }

        public Builder mount(String entityId) {
            this.mountEntity = entityId;
            return this;
        }

        public Builder mainhand(String weaponId) {
            this.mainhandWeapon = weaponId;
            return this;
        }

        public Phase build() {
            return new Phase(id, hpThreshold, passives, actives, onTransition, mountEntity, mainhandWeapon);
        }
    }
}
