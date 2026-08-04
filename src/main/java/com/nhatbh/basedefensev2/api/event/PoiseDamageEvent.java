package com.nhatbh.basedefensev2.api.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import javax.annotation.Nullable;

/**
 * Fired when an entity is about to receive damage to its poise (strength).
 * This event is {@link Cancelable}. If canceled, no poise damage will be applied.
 * External mods can modify the poise damage amount using {@link #setAmount(float)}.
 */
@Cancelable
public class PoiseDamageEvent extends Event {
    private final LivingEntity entity;
    private final LivingEntity attacker;
    private final DamageSource source;
    private final float originalAmount;
    private float amount;

    public PoiseDamageEvent(LivingEntity entity, @Nullable LivingEntity attacker, @Nullable DamageSource source, float amount) {
        this.entity = entity;
        this.attacker = attacker;
        this.source = source;
        this.originalAmount = amount;
        this.amount = amount;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    @Nullable
    public LivingEntity getAttacker() {
        return attacker;
    }

    @Nullable
    public DamageSource getSource() {
        return source;
    }

    public float getOriginalAmount() {
        return originalAmount;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = Math.max(0, amount);
    }
}
