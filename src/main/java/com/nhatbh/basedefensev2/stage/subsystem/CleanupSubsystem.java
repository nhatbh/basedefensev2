package com.nhatbh.basedefensev2.stage.subsystem;

import com.mojang.logging.LogUtils;
import com.nhatbh.basedefensev2.stage.events.WaveEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens to WaveEvents.StageEnded and:
 *  1. Removes all non-player entities inside the arena bounds (mobs, drops, arrows)
 *  2. Teleports all players in the arena back to overworld spawn
 */
public class CleanupSubsystem {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onStageEnded(WaveEvents.StageEnded event) {
        ServerLevel arena = event.getLevel();

        List<Entity> toRemove = new ArrayList<>();
        List<ServerPlayer> toTeleport = new ArrayList<>();

        for (Entity entity : arena.getAllEntities()) {
            if (!event.getArenaBounds().contains(entity.position())) continue;

            if (entity instanceof ServerPlayer player) {
                toTeleport.add(player);
            } else {
                toRemove.add(entity);
            }
        }

        // 1. Wipe non-player entities
        for (Entity entity : toRemove) {
            entity.discard();
        }
        LOGGER.info("[CleanupSubsystem] Discarded {} entities.", toRemove.size());

        // 2. Teleport players to overworld spawn
        ServerLevel overworld = arena.getServer().overworld();
        BlockPos worldSpawn = overworld.getSharedSpawnPos();
        double sx = worldSpawn.getX() + 0.5;
        double sy = worldSpawn.getY();
        double sz = worldSpawn.getZ() + 0.5;

        for (ServerPlayer player : toTeleport) {
            player.teleportTo(overworld, sx, sy, sz,
                    player.getYRot(), player.getXRot());
            LOGGER.info("[CleanupSubsystem] Teleported player '{}'.",
                    player.getName().getString());
        }
    }
}
