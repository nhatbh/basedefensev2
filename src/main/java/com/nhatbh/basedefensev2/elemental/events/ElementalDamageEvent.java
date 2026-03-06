package com.nhatbh.basedefensev2.elemental.events;

import com.nhatbh.basedefensev2.elemental.ElementType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

public class ElementalDamageEvent extends Event {
    private final LivingEntity target;
    private final LivingEntity source;
    private final ElementType element;
    private final float baseDamage;

    public ElementalDamageEvent(LivingEntity target, LivingEntity source, ElementType element, float baseDamage) {
        this.target = target;
        this.source = source;
        this.element = element;
        this.baseDamage = baseDamage;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public LivingEntity getSource() {
        return source;
    }

    public ElementType getElement() {
        return element;
    }

    public float getBaseDamage() {
        return baseDamage;
    }
}
