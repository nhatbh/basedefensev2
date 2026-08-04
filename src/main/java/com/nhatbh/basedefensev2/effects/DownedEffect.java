package com.nhatbh.basedefensev2.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class DownedEffect extends MobEffect {
    public DownedEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000); // Dark red color
    }
}
