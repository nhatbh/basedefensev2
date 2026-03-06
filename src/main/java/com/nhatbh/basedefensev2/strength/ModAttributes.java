package com.nhatbh.basedefensev2.strength;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, "basedefensev2");

    public static final RegistryObject<Attribute> STRENGTH_DAMAGE_MULTIPLIER = ATTRIBUTES.register("strength_damage_multiplier",
            () -> new RangedAttribute("attribute.name.strength_damage_multiplier", 1.0D, 0.0D, 1024.0D).setSyncable(true));
}
