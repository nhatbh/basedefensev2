package com.nhatbh.basedefensev2.registry;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.effects.PetrificationEffect;
import com.nhatbh.basedefensev2.effects.PetrifiedEffect;
import com.nhatbh.basedefensev2.effects.StaticShockEffect;
import com.nhatbh.basedefensev2.effects.SunforgedEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, BaseDefenseMod.MODID);

    public static final RegistryObject<MobEffect> STAGGER = MOB_EFFECTS.register("stagger", () -> new CustomEffect(MobEffectCategory.HARMFUL, 0x888888));
    public static final RegistryObject<MobEffect> HEAVY_STAGGER = MOB_EFFECTS.register("heavy_stagger", () -> new CustomEffect(MobEffectCategory.HARMFUL, 0x444444));
    public static final RegistryObject<MobEffect> FLOORED = MOB_EFFECTS.register("floored", () -> new CustomEffect(MobEffectCategory.HARMFUL, 0x222222));
    public static final RegistryObject<MobEffect> SEARED = MOB_EFFECTS.register("seared", () -> new CustomEffect(MobEffectCategory.HARMFUL, 0xD35400));
    public static final RegistryObject<MobEffect> HEALING_BLOCK = MOB_EFFECTS.register("healing_block", () -> new CustomEffect(MobEffectCategory.HARMFUL, 0x7F8C8D));
    public static final RegistryObject<MobEffect> PETRIFICATION = MOB_EFFECTS.register("petrification", PetrificationEffect::new);
    public static final RegistryObject<MobEffect> PETRIFIED = MOB_EFFECTS.register("petrified", PetrifiedEffect::new);
    public static final RegistryObject<MobEffect> STATIC_SHOCK = MOB_EFFECTS.register("static_shock", StaticShockEffect::new);

    public static final RegistryObject<MobEffect> SUN_WARD = MOB_EFFECTS.register("sun_ward", SunforgedEffects.SunWardEffect::new);
    public static final RegistryObject<MobEffect> SUNWARD_IMMUNITY = MOB_EFFECTS.register("sunward_immunity", SunforgedEffects.SunwardImmunityEffect::new);
    public static final RegistryObject<MobEffect> HALO = MOB_EFFECTS.register("halo", SunforgedEffects.HaloEffect::new);
    public static final RegistryObject<MobEffect> SUNLESS = MOB_EFFECTS.register("sunless", SunforgedEffects.SunlessEffect::new);

    public static final RegistryObject<MobEffect> HEAVY_FOOTING = MOB_EFFECTS.register("heavy_footing", com.nhatbh.basedefensev2.effects.HeavyFootingEffect::new);
    public static final RegistryObject<MobEffect> UNTARGETABLE = MOB_EFFECTS.register("untargetable", com.nhatbh.basedefensev2.effects.UntargetableEffect::new);
    public static final RegistryObject<MobEffect> DOWNED = MOB_EFFECTS.register("downed", com.nhatbh.basedefensev2.effects.DownedEffect::new);
    public static final RegistryObject<MobEffect> SEISMIC_RUPTURE = MOB_EFFECTS.register("seismic_rupture", () -> new CustomEffect(MobEffectCategory.BENEFICIAL, 0x8B5A2B));
    public static final RegistryObject<MobEffect> SHATTERED_MANTLE = MOB_EFFECTS.register("shattered_mantle", () -> new CustomEffect(MobEffectCategory.HARMFUL, 0x553311));

    public static class CustomEffect extends MobEffect {
        public CustomEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
