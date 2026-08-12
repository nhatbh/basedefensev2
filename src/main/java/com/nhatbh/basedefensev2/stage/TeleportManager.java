package com.nhatbh.basedefensev2.stage;

import com.nhatbh.basedefensev2.stage.config.StageConfig;
import com.nhatbh.basedefensev2.stage.core.StageContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles teleportation logic to and from the arena dimension.
 */
public class TeleportManager {

    private static final Map<UUID, TeleportRequest> pendingTeleports = new HashMap<>();

    public static void requestJoin(ServerPlayer player) {
        if (pendingTeleports.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cYou are already preparing to teleport!"));
            return;
        }

        player.sendSystemMessage(Component.literal("§ePreparing for teleport... §cStand still for 5 seconds."));
        pendingTeleports.put(player.getUUID(), new TeleportRequest(player.getUUID(), player.position()));
    }

    public static void requestLeave(ServerPlayer player) {
        teleportBack(player);
    }

    public static void forceTeleportAll(ServerLevel arenaLevel) {
        if (arenaLevel == null || arenaLevel.getServer() == null)
            return;
        arenaLevel.getServer().getPlayerList().getPlayers().forEach(player -> {
            if (player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SPECTATOR
                    && !player.level().dimension().equals(ModDimensions.ARENA)) {
                teleportToArena(player, arenaLevel);
                player.sendSystemMessage(
                        Component.literal("§6[The Rift] §eThe ancients summon you! Step forth and fight!"));
            }
        });
    }

    public static void tick() {
        pendingTeleports.values().removeIf(TeleportRequest::tick);
    }

    public static void savePreArenaPosition(ServerPlayer player) {
        if (player == null || player.level().dimension().equals(ModDimensions.ARENA)) {
            return;
        }
        CompoundTag nbt = player.getPersistentData();
        nbt.putString("BDV2_PreArenaDim", player.level().dimension().location().toString());
        nbt.putDouble("BDV2_PreArenaX", player.getX());
        nbt.putDouble("BDV2_PreArenaY", player.getY());
        nbt.putDouble("BDV2_PreArenaZ", player.getZ());
        nbt.putFloat("BDV2_PreArenaYRot", player.getYRot());
        nbt.putFloat("BDV2_PreArenaXRot", player.getXRot());
    }

    public static void teleportToArena(ServerPlayer player, ServerLevel arenaLevel) {
        StageContext ctx = StageContext.getOrCreate(arenaLevel);
        if (!ctx.isActive())
            return;

        savePreArenaPosition(player);

        StageConfig.SpawnArea area = ctx.getSpawnArea();
        player.teleportTo(arenaLevel, area.x, area.y, area.z, player.getYRot(), player.getXRot());
        StageContext.restorePlayerItemDurability(player);
        player.sendSystemMessage(Component.literal("§aYou have stepped into The Rift!"));
    }

    public static void teleportBack(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;

        CompoundTag nbt = player.getPersistentData();
        if (nbt.contains("BDV2_PreArenaDim")) {
            try {
                ResourceLocation dimLoc = ResourceLocation.parse(nbt.getString("BDV2_PreArenaDim"));
                ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
                ServerLevel targetLevel = player.getServer().getLevel(dimKey);
                if (targetLevel != null) {
                    double x = nbt.getDouble("BDV2_PreArenaX");
                    double y = nbt.getDouble("BDV2_PreArenaY");
                    double z = nbt.getDouble("BDV2_PreArenaZ");
                    float yRot = nbt.getFloat("BDV2_PreArenaYRot");
                    float xRot = nbt.getFloat("BDV2_PreArenaXRot");

                    player.teleportTo(targetLevel, x, y, z, yRot, xRot);
                    player.sendSystemMessage(Component.literal("§aReturned to your original location."));
                    return;
                }
            } catch (Exception ignored) {}
        }

        teleportToSpawnAnchor(player);
    }

    public static void teleportToSpawnAnchor(ServerPlayer player) {
        ServerLevel respawnLevel = player.getServer().getLevel(player.getRespawnDimension());
        if (respawnLevel == null)
            respawnLevel = player.getServer().overworld();

        Vec3 respawnPos = player.getRespawnPosition() != null ? Vec3.atCenterOf(player.getRespawnPosition())
                : Vec3.atCenterOf(respawnLevel.getSharedSpawnPos());

        player.teleportTo(respawnLevel, respawnPos.x, respawnPos.y, respawnPos.z, player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("§aReturned home safely."));
    }

    private static class TeleportRequest {
        private final UUID playerUUID;
        private final Vec3 startPos;
        private int ticksRemaining = 100; // 5 seconds

        public TeleportRequest(UUID playerUUID, Vec3 startPos) {
            this.playerUUID = playerUUID;
            this.startPos = startPos;
        }

        public boolean tick() {
            ServerPlayer player = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList()
                    .getPlayer(playerUUID);
            if (player == null)
                return true;

            // Movement check
            if (player.position().distanceToSqr(startPos) > 0.25) {
                player.sendSystemMessage(Component.literal("§cTeleport cancelled: You moved!"));
                return true;
            }

            ticksRemaining--;
            if (ticksRemaining <= 0) {
                if (player.getServer() != null) {
                    ServerLevel arenaLevel = player.getServer().getLevel(ModDimensions.ARENA);
                    if (arenaLevel != null) {
                        teleportToArena(player, arenaLevel);
                    }
                }
                return true;
            }

            if (ticksRemaining % 20 == 0) {
                player.sendSystemMessage(Component.literal("§eTeleporting in §c" + (ticksRemaining / 20) + "s§e..."));
            }

            return false;
        }
    }
}
