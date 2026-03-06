package com.nhatbh.basedefensev2;

import com.mojang.logging.LogUtils;
import com.nhatbh.basedefensev2.strength.ModAttributes;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BaseDefenseMod.MODID)
public class BaseDefenseMod {
    public static final String MODID = "basedefensev2";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BaseDefenseMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        ModAttributes.ATTRIBUTES.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkManager.register();
        });
        
        com.nhatbh.basedefensev2.elemental.MobElementConfig.load();
        com.nhatbh.basedefensev2.config.SpellPenaltyConfig.load();
        
        LOGGER.info("Base Defense V2 initialized!");
    }
}
