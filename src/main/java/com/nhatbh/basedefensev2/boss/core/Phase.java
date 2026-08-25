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
    private final String mainhandNbt;
    private final String helmet;
    private final String chestplate;
    private final String leggings;
    private final String boots;
    private final Float scale;

    public Phase(int id, float hpThreshold, List<PassiveSkill> passives, List<ActiveSkillEntry> actives, Consumer<SkillContext> onTransition, String mountEntity, String mainhandWeapon, String mainhandNbt, String helmet, String chestplate, String leggings, String boots, Float scale) {
        this.id = id;
        this.hpThreshold = hpThreshold;
        this.passives = passives;
        this.actives = actives;
        this.onTransition = onTransition;
        this.mountEntity = mountEntity;
        this.mainhandWeapon = mainhandWeapon;
        this.mainhandNbt = mainhandNbt;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.scale = scale;
    }
    public int getId() { return id; }
    public float getHpThreshold() { return hpThreshold; }
    public List<PassiveSkill> getPassives() { return passives; }
    public List<ActiveSkillEntry> getActives() { return actives; }
    public String getMountEntity() { return mountEntity; }
    public String getMainhandWeapon() { return mainhandWeapon; }
    public String getMainhandNbt() { return mainhandNbt; }
    public String getHelmet() { return helmet; }
    public String getChestplate() { return chestplate; }
    public String getLeggings() { return leggings; }
    public String getBoots() { return boots; }
    public Float getScale() { return scale; }

    public void onEnter(LivingEntity boss) {
        if (scale != null) {
            BossManager.applyScale(boss, scale);
        }
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
        if (com.nhatbh.basedefensev2.api.PoiseAPI.isExhausted(boss)) {
            return;
        }
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
        private String mainhandNbt = null;
        private String helmet = null;
        private String chestplate = null;
        private String leggings = null;
        private String boots = null;
        private Float scale = null;

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

        public Builder mainhand(String weaponId, String nbt) {
            this.mainhandWeapon = weaponId;
            this.mainhandNbt = nbt;
            return this;
        }

        public Builder helmet(String itemId) { this.helmet = itemId; return this; }
        public Builder chestplate(String itemId) { this.chestplate = itemId; return this; }
        public Builder leggings(String itemId) { this.leggings = itemId; return this; }
        public Builder boots(String itemId) { this.boots = itemId; return this; }
        
        public Builder armor(String helmet, String chest, String legs, String boots) {
            this.helmet = helmet;
            this.chestplate = chest;
            this.leggings = legs;
            this.boots = boots;
            return this;
        }

        public Builder scale(float scale) {
            this.scale = scale;
            return this;
        }

        public Phase build() {
            return new Phase(id, hpThreshold, passives, actives, onTransition, mountEntity, mainhandWeapon, mainhandNbt, helmet, chestplate, leggings, boots, scale);
        }
    }
}
