package com.nhatbh.basedefensev2.registry;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BaseDefenseMod.MODID);

    public static final RegistryObject<Item> MYTHIC_BLADE = ITEMS.register("mythicblade", 
            () -> new Item(new Item.Properties().stacksTo(1)));
}
