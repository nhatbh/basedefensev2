package com.nhatbh.basedefensev2.events;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.registry.ModEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID)
public class PetrificationEventHandler {

    // Prevent Jumping when Petrified
    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity().hasEffect(ModEffects.PETRIFIED.get())) {
            event.getEntity().setDeltaMovement(0, 0, 0);
        }
    }

    // Prevent Attacking when Petrified
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().hasEffect(ModEffects.PETRIFIED.get())) {
            event.setCanceled(true);
        }
    }

    // Prevent Item Usage / Right Click Item when Petrified
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().hasEffect(ModEffects.PETRIFIED.get())) {
            event.setCanceled(true);
        }
    }

    // Prevent Block Interaction / Right Click Block when Petrified
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().hasEffect(ModEffects.PETRIFIED.get())) {
            event.setCanceled(true);
        }
    }

    // Prevent Entity Interaction when Petrified
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().hasEffect(ModEffects.PETRIFIED.get())) {
            event.setCanceled(true);
        }
    }

    // Prevent Left Click Block Digging when Petrified
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().hasEffect(ModEffects.PETRIFIED.get())) {
            event.setCanceled(true);
        }
    }

    // Prevent Block Breaking when Petrified
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player != null && player.hasEffect(ModEffects.PETRIFIED.get())) {
            event.setCanceled(true);
        }
    }

    // Prevent Item Use Start (eating, drinking, charging bow/spells) when Petrified
    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity().hasEffect(ModEffects.PETRIFIED.get())) {
            event.setCanceled(true);
        }
    }
}
