package com.nhatbh.basedefensev2.stage;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraft.server.level.ServerPlayer;
import com.nhatbh.basedefensev2.stage.core.StageContext;
import com.nhatbh.basedefensev2.stage.core.StageState;
import net.minecraft.network.chat.Component;
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

    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        // If the player is IN the Arena dimension and trying to leave
        if (player.level().dimension().equals(ModDimensions.ARENA)) {
            StageContext ctx = StageContext.getOrCreate((net.minecraft.server.level.ServerLevel) player.level());
            
            // Block leaving if stage is active and not in Scavenge/Ended state
            if (ctx.isActive()) {
                StageState state = ctx.getStageState();
                if (state != StageState.SCAVENGE && state != StageState.ENDED) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cThe spatial rift is too unstable to exit right now!"));
                }
            }
        }
    }
}
