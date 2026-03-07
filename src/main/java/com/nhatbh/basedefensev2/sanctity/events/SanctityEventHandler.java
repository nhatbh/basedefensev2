package com.nhatbh.basedefensev2.sanctity.events;

import com.nhatbh.basedefensev2.sanctity.data.AltarSavedData;
import com.nhatbh.basedefensev2.sanctity.network.SanctitySyncPacket;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SanctityEventHandler {
    private static final int RESPAWN_TICKS = 600; // 30 seconds

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            AltarSavedData data = AltarSavedData.get(level);

            // Prevent actual death
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth()); // Reset health to prevent immediate re-death logic triggers
            player.setGameMode(GameType.SPECTATOR);

            int newSanctity = data.deductSanctity(50);
            syncToAll(data);

            if (newSanctity > 0) {
                data.addRespawn(player.getUUID(), RESPAWN_TICKS);
                player.displayClientMessage(Component.literal("Respawning in " + (RESPAWN_TICKS / 20) + "s").withStyle(ChatFormatting.YELLOW), true);
            } else {
                triggerGameOver((ServerLevel) player.level());
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // We need a level to get the data, overworld is usually safe
            ServerLevel overworld = event.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
            if (overworld == null) return;

            AltarSavedData data = AltarSavedData.get(overworld);
            
            // Handle Grace Regeneration
            data.regenGrace();
            
            // Sync to all players every second (20 ticks) to update HUD grace
            if (overworld.getGameTime() % 20 == 0) {
                syncToAll(data);
                
                // Actionbar for respawning players
                for (Map.Entry<UUID, Integer> entry : data.getRespawnQueue().entrySet()) {
                    ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
                    if (player != null) {
                        int secondsRemaining = entry.getValue() / 20;
                        if (secondsRemaining > 0) {
                            player.displayClientMessage(Component.literal("Respawning in " + secondsRemaining + "s").withStyle(ChatFormatting.YELLOW), true);
                        }
                    }
                }
            }

            Map<UUID, Integer> ready = data.tickRespawns();

            for (UUID uuid : ready.keySet()) {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) {
                    revivePlayer(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AltarSavedData data = AltarSavedData.get((ServerLevel) player.level());
            var config = com.nhatbh.basedefensev2.config.SanctityConfig.data;
            NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), 
                new SanctitySyncPacket(data.getSanctity(), data.getGrace(), config.maxSanctity, config.maxGrace));
        }
    }

    private static void revivePlayer(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        
        // Get respawn position
        BlockPos respawnPos = player.getRespawnPosition();
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel respawnLevel = server.getLevel(player.getRespawnDimension());
        
        if (respawnLevel == null || respawnPos == null) {
            respawnLevel = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
            respawnPos = respawnLevel != null ? respawnLevel.getSharedSpawnPos() : BlockPos.ZERO;
        }

        if (respawnLevel != null) {
            // Teleport
            Vec3 target = Vec3.atCenterOf(respawnPos);
            player.teleportTo(respawnLevel, target.x(), target.y(), target.z(), player.getYRot(), player.getXRot());
        }
        
        player.sendSystemMessage(Component.translatable("message.basedefensev2.revived").withStyle(ChatFormatting.GREEN));
    }

    private static void triggerGameOver(ServerLevel level) {
        AltarSavedData data = AltarSavedData.get(level);
        data.clearRespawnQueue();

        net.minecraft.server.MinecraftServer server = level.getServer();

        Component gameOverMessage = Component.literal("GAME OVER").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        server.getPlayerList().getPlayers().forEach(player -> {
            player.setGameMode(GameType.SPECTATOR);
            player.connection.send(new ClientboundSetTitleTextPacket(gameOverMessage));
        });
    }

    private static void syncToAll(AltarSavedData data) {
        var config = com.nhatbh.basedefensev2.config.SanctityConfig.data;
        NetworkManager.INSTANCE.send(PacketDistributor.ALL.noArg(), 
            new SanctitySyncPacket(data.getSanctity(), data.getGrace(), config.maxSanctity, config.maxGrace));
    }
}
