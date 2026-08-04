package com.nhatbh.basedefensev2;

import com.mojang.logging.LogUtils;
import com.nhatbh.basedefensev2.stage.ArenaDimensionTickHandler;
import com.nhatbh.basedefensev2.stage.StageLoader;
import com.nhatbh.basedefensev2.stage.subsystem.CleanupSubsystem;
import com.nhatbh.basedefensev2.stage.subsystem.RewardSubsystem;
import com.nhatbh.basedefensev2.stage.subsystem.SpawnerSubsystem;
import com.nhatbh.basedefensev2.stage.ArenaCommands;
// Boss entities use ModBosses registry now
import com.nhatbh.basedefensev2.registry.ModEntities;
import com.nhatbh.basedefensev2.strength.ModAttributes;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import com.nhatbh.basedefensev2.sanctity.data.ReviveState;
import com.nhatbh.basedefensev2.sanctity.data.ReviveStateProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
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

    public BaseDefenseMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        com.nhatbh.basedefensev2.strength.network.NetworkManager.register();

        modEventBus.addListener(this::commonSetup);

        ModAttributes.ATTRIBUTES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        com.nhatbh.basedefensev2.registry.ModItems.ITEMS.register(modEventBus);
        com.nhatbh.basedefensev2.registry.ModEffects.MOB_EFFECTS.register(modEventBus);

        modEventBus.addListener(this::onAttributeCreation);
        modEventBus.addListener(this::onAttributeModification);
        modEventBus.addListener(this::registerCapabilities);

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

        // Arena protection
        MinecraftForge.EVENT_BUS.register(com.nhatbh.basedefensev2.stage.ArenaProtectionHandler.class);

        // Stage mob drops and XP
        MinecraftForge.EVENT_BUS.register(com.nhatbh.basedefensev2.stage.events.StageMobDropsHandler.class);

        // Auto leveling system
        MinecraftForge.EVENT_BUS.register(com.nhatbh.basedefensev2.level.MobLevelEventHandler.class);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.nhatbh.basedefensev2.strength.network.NetworkManager.register();
        });

        com.nhatbh.basedefensev2.elemental.MobElementConfig.load();
        com.nhatbh.basedefensev2.config.SpellPenaltyConfig.load();
        com.nhatbh.basedefensev2.config.SanctityConfig.load();
        com.nhatbh.basedefensev2.classification.ClassificationManager.load();

        com.nhatbh.basedefensev2.boss.impl.spells.BossSpellCaster.init();

        com.nhatbh.basedefensev2.boss.impl.generic.ZombieTestBoss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_1.InfernalDragonBoss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_1.WadjetMiniboss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_2.YetiBoss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_3.WadjetMiniboss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_3.HarbingerBoss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_4.ProwlerMiniboss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_4.AncientRemnantBoss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_5.CoralssusMiniboss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_5.LeviathanBoss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_6.EnderGolemMiniboss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_6.EnderGuardianBoss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_7.NetheriteMonstrosityMiniboss.register();
        com.nhatbh.basedefensev2.boss.impl.stage_7.IgnisBoss.register();
    }

    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        // Boss entities have been refactored to use standard entities with components

    }

    private void onAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, ModAttributes.STRENGTH_DAMAGE_MULTIPLIER.get());
        for (EntityType<? extends net.minecraft.world.entity.LivingEntity> entityType : event.getTypes()) {
            event.add(entityType, ModAttributes.STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get());
            event.add(entityType, ModAttributes.SPECIAL_STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get());
        }
    }

    /**
     * Register the StageLoader as a server-side resource reload listener so it
     * re-parses data/basedefensev2/stages/*.json whenever /reload is run.
     */
    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(StageLoader.INSTANCE);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(ReviveState.class);
    }

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            if (!event.getObject().getCapability(ReviveStateProvider.REVIVE_STATE).isPresent()) {
                event.addCapability(ResourceLocation.fromNamespaceAndPath(MODID, "revive_state"),
                        new ReviveStateProvider());
            }
        }
    }

    @SubscribeEvent
    public void onPlayerCloned(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getOriginal().getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(old -> {
                event.getEntity().getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(newCap -> {
                    newCap.copyFrom(old);
                });
            });
        }
    }
}
