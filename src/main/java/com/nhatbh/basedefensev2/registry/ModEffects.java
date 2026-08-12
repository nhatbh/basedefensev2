package com.nhatbh.basedefensev2.registry;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.effects.PetrificationEffect;
import com.nhatbh.basedefensev2.effects.PetrifiedEffect;
import com.nhatbh.basedefensev2.effects.StaticShockEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,
            BaseDefenseMod.MODID);

    public static final RegistryObject<MobEffect> SEARED = MOB_EFFECTS.register("seared",
            () -> new CustomEffect(MobEffectCategory.HARMFUL, 0xD35400));
    public static final RegistryObject<MobEffect> HEALING_BLOCK = MOB_EFFECTS.register("healing_block",
            () -> new CustomEffect(MobEffectCategory.HARMFUL, 0x7F8C8D));
    public static final RegistryObject<MobEffect> PETRIFICATION = MOB_EFFECTS.register("petrification",
            PetrificationEffect::new);
    public static final RegistryObject<MobEffect> PETRIFIED = MOB_EFFECTS.register("petrified", PetrifiedEffect::new);
    public static final RegistryObject<MobEffect> STATIC_SHOCK = MOB_EFFECTS.register("static_shock",
            StaticShockEffect::new);
    public static final RegistryObject<MobEffect> SUFFOCATION = MOB_EFFECTS.register("suffocation",
            com.nhatbh.basedefensev2.effects.SuffocationEffect::new);


    public static final RegistryObject<MobEffect> UNTARGETABLE = MOB_EFFECTS.register("untargetable",
            com.nhatbh.basedefensev2.effects.UntargetableEffect::new);
    public static final RegistryObject<MobEffect> DOWNED = MOB_EFFECTS.register("downed",
            com.nhatbh.basedefensev2.effects.DownedEffect::new);
    public static final RegistryObject<MobEffect> SHATTERED_MANTLE = MOB_EFFECTS.register("shattered_mantle",
            () -> new CustomEffect(MobEffectCategory.HARMFUL, 0x553311));
    public static final RegistryObject<MobEffect> RIPOSTE = MOB_EFFECTS.register("riposte",
            com.nhatbh.basedefensev2.effects.RiposteEffect::new);
    public static final RegistryObject<MobEffect> SUPPRESSION = MOB_EFFECTS.register("suppression",
            com.nhatbh.basedefensev2.effects.SuppressionEffect::new);

    public static class CustomEffect extends MobEffect {
        public CustomEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
