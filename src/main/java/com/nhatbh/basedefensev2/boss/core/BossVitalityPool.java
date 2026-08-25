package com.nhatbh.basedefensev2.boss.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

public class BossVitalityPool {
    public static final String NBT_MAX_VITALITY = "bdv2_max_vitality";
    public static final String NBT_CURRENT_VITALITY = "bdv2_current_vitality";

    private double maxVitality = 100.0;
    private double currentVitality = 100.0;

    public BossVitalityPool() {}

    public BossVitalityPool(double maxVitality) {
        initialize(maxVitality);
    }

    public void initialize(double maxVitality) {
        this.maxVitality = Math.max(1.0, maxVitality);
        this.currentVitality = this.maxVitality;
    }

    public double getMaxVitality() {
        return maxVitality;
    }

    public double getCurrentVitality() {
        return currentVitality;
    }

    public void setMaxVitality(double maxVitality) {
        this.maxVitality = Math.max(1.0, maxVitality);
        if (this.currentVitality > this.maxVitality) {
            this.currentVitality = this.maxVitality;
        }
    }

    public void setCurrentVitality(double currentVitality) {
        this.currentVitality = Math.max(0.0, Math.min(this.maxVitality, currentVitality));
    }

    public double damage(double amount) {
        if (amount <= 0) return 0;
        double actualDamage = Math.min(this.currentVitality, amount);
        this.currentVitality = Math.max(0.0, this.currentVitality - amount);
        return actualDamage;
    }

    public double heal(double amount) {
        if (amount <= 0) return 0;
        double actualHeal = Math.min(this.maxVitality - this.currentVitality, amount);
        this.currentVitality = Math.min(this.maxVitality, this.currentVitality + amount);
        return actualHeal;
    }

    public double getRatio() {
        if (maxVitality <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, currentVitality / maxVitality));
    }

    public boolean isDead() {
        return currentVitality <= 0;
    }

    /**
     * Synchronizes the entity's real vanilla health float based on the vitality ratio
     * and entity default max health attribute.
     */
    public void syncToVanillaHealth(LivingEntity boss) {
        syncToVanillaHealth(boss, null, null);
    }

    public void syncToVanillaHealth(LivingEntity boss, net.minecraft.world.damagesource.DamageSource source, LivingEntity attacker) {
        if (boss == null || boss.level().isClientSide) return;

        float defaultMaxHp = boss.getMaxHealth();
        if (defaultMaxHp <= 0) defaultMaxHp = 20.0f;

        if (isDead()) {
            boss.setHealth(0.0f);
            net.minecraft.world.damagesource.DamageSource killSource = (source != null) ? source : (attacker != null ? boss.damageSources().mobAttack(attacker) : boss.damageSources().fellOutOfWorld());
            boss.hurt(killSource, 999999f);
        } else {
            float targetHp = (float) (defaultMaxHp * getRatio());
            // Keep at least 0.01f health while alive so vanilla entity doesn't die prematurely
            boss.setHealth(Math.max(0.01f, targetHp));
        }
    }

    public void saveToNBT(CompoundTag tag) {
        if (tag != null) {
            tag.putDouble(NBT_MAX_VITALITY, maxVitality);
            tag.putDouble(NBT_CURRENT_VITALITY, currentVitality);
        }
    }

    public void loadFromNBT(CompoundTag tag) {
        if (tag != null && tag.contains(NBT_MAX_VITALITY)) {
            this.maxVitality = Math.max(1.0, tag.getDouble(NBT_MAX_VITALITY));
            if (tag.contains(NBT_CURRENT_VITALITY)) {
                this.currentVitality = Math.max(0.0, Math.min(this.maxVitality, tag.getDouble(NBT_CURRENT_VITALITY)));
            } else {
                this.currentVitality = this.maxVitality;
            }
        }
    }
}
