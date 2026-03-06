package com.nhatbh.basedefensev2;

import com.mojang.logging.LogUtils;
import com.nhatbh.basedefensev2.stage.ArenaDimensionTickHandler;
import com.nhatbh.basedefensev2.stage.StageLoader;
import com.nhatbh.basedefensev2.stage.subsystem.CleanupSubsystem;
import com.nhatbh.basedefensev2.stage.subsystem.RewardSubsystem;
import com.nhatbh.basedefensev2.stage.subsystem.SpawnerSubsystem;
import com.nhatbh.basedefensev2.stage.ArenaCommands;
import com.nhatbh.basedefensev2.strength.ModAttributes;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

        // Register this class and all stage subsystems on the Forge event bus
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ArenaDimensionTickHandler());
        MinecraftForge.EVENT_BUS.register(new SpawnerSubsystem());
        MinecraftForge.EVENT_BUS.register(new RewardSubsystem());
        MinecraftForge.EVENT_BUS.register(new CleanupSubsystem());
        
        // Arena commands and teleportation
        MinecraftForge.EVENT_BUS.register(ArenaCommands.class);
        
        // Sanctity system
        MinecraftForge.EVENT_BUS.register(com.nhatbh.basedefensev2.sanctity.events.SanctityEventHandler.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkManager.register();
        });

        com.nhatbh.basedefensev2.elemental.MobElementConfig.load();
        com.nhatbh.basedefensev2.config.SpellPenaltyConfig.load();
        com.nhatbh.basedefensev2.config.SanctityConfig.load();

        LOGGER.info("Base Defense V2 initialized!");
    }

    /**
     * Register the StageLoader as a server-side resource reload listener so it
     * re-parses data/basedefensev2/stages/*.json whenever /reload is run.
     */
    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(StageLoader.INSTANCE);
    }
}
