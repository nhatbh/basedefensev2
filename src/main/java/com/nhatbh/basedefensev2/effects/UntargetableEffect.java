package com.nhatbh.basedefensev2.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class UntargetableEffect extends MobEffect {
    public UntargetableEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8A2BE2); // Translucent / mystic purple color
    }
}
