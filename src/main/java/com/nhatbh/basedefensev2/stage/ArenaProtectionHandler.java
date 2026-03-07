package com.nhatbh.basedefensev2.stage;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Prevents block breaking, placing, interactions, and explosion damage inside the arena dimension.
 * Allows water handling with buckets.
 */
public class ArenaProtectionHandler {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && level.dimension().equals(ModDimensions.ARENA)) {
            if (!event.getPlayer().isCreative()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && level.dimension().equals(ModDimensions.ARENA)) {
            // We might want to allow some entities to place blocks if it's part of the mod,
            // but for now, block all non-creative placement.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().dimension().equals(ModDimensions.ARENA)) {
            // Allow bucket usage (water placement/removal)
            if (event.getItemStack().getItem() instanceof BucketItem) {
                return;
            }
            if (!event.getEntity().isCreative()) {
                event.setUseBlock(Event.Result.DENY);
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().dimension().equals(ModDimensions.ARENA)) {
            if (!event.getEntity().isCreative()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension().equals(ModDimensions.ARENA)) {
            // Prevent explosions from destroying blocks
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    public static void onBucketUse(FillBucketEvent event) {
        // Explicitly allow bucket usage if it's relevant to water,
        // though RightClickBlock already filters it.
        // If we wanted to block everything BUT water, we'd check the bucket content here.
    }
}
