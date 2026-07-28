package com.nhatbh.basedefensev2.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class SunforgedEffects {

    public static class SunWardEffect extends MobEffect {
        public SunWardEffect() {
            super(MobEffectCategory.HARMFUL, 0xFFD700);
        }
    }

    public static class SunwardImmunityEffect extends MobEffect {
        public SunwardImmunityEffect() {
            super(MobEffectCategory.BENEFICIAL, 0xFFFFA0);
        }
    }

    public static class HaloEffect extends MobEffect {
        public HaloEffect() {
            super(MobEffectCategory.BENEFICIAL, 0xFFAA00);
        }
    }

    public static class SunlessEffect extends MobEffect {
        public SunlessEffect() {
            super(MobEffectCategory.HARMFUL, 0x4B0082);
        }
    }
}
