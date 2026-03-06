package com.nhatbh.basedefensev2.sanctity.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AltarSavedData extends SavedData {
    private static final String DATA_NAME = "basedefense_altar_data";

    private int sanctity = com.nhatbh.basedefensev2.config.SanctityConfig.data.maxSanctity;
    private double grace = 0;
    private final Map<UUID, Integer> respawnQueue = new HashMap<>();

    public AltarSavedData() {
    }

    public static AltarSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) overworld = level; // Fallback
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(AltarSavedData::load, AltarSavedData::new, DATA_NAME);
    }

    public static AltarSavedData load(CompoundTag tag) {
        AltarSavedData data = new AltarSavedData();
        data.sanctity = tag.getInt("Sanctity");
        data.grace = tag.getDouble("Grace");

        ListTag queueList = tag.getList("RespawnQueue", Tag.TAG_COMPOUND);
        for (int i = 0; i < queueList.size(); i++) {
            CompoundTag entry = queueList.getCompound(i);
            UUID uuid = entry.getUUID("UUID");
            int ticks = entry.getInt("Ticks");
            data.respawnQueue.put(uuid, ticks);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Sanctity", sanctity);
        tag.putDouble("Grace", grace);

        ListTag queueList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : respawnQueue.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("UUID", entry.getKey());
            entryTag.putInt("Ticks", entry.getValue());
            queueList.add(entryTag);
        }
        tag.put("RespawnQueue", queueList);

        return tag;
    }

    public int getSanctity() {
        return sanctity;
    }

    public void setSanctity(int sanctity) {
        this.sanctity = sanctity;
        setDirty();
    }

    public int deductSanctity(int amount) {
        this.sanctity = Math.max(0, this.sanctity - amount);
        setDirty();
        return this.sanctity;
    }

    public double getGrace() {
        return grace;
    }

    public void setGrace(double grace) {
        this.grace = Math.min(com.nhatbh.basedefensev2.config.SanctityConfig.data.maxGrace, grace);
        setDirty();
    }

    public void regenGrace() {
        double rate = com.nhatbh.basedefensev2.config.SanctityConfig.data.graceRegenRate;
        if (rate > 0 && grace < com.nhatbh.basedefensev2.config.SanctityConfig.data.maxGrace) {
            this.grace = Math.min(com.nhatbh.basedefensev2.config.SanctityConfig.data.maxGrace, grace + rate);
            setDirty();
        }
    }

    public void addRespawn(UUID playerUUID, int ticks) {
        respawnQueue.put(playerUUID, ticks);
        setDirty();
    }

    public Map<UUID, Integer> getRespawnQueue() {
        return respawnQueue;
    }

    public void clearRespawnQueue() {
        respawnQueue.clear();
        setDirty();
    }

    /**
     * Decrements timers and returns a list of UUIDs that have hit 0 and are ready to be revived.
     */
    public Map<UUID, Integer> tickRespawns() {
        Map<UUID, Integer> readyToRevive = new HashMap<>();
        
        // Use a copy to avoid ConcurrentModificationException if needed, 
        // though we are just iterating and putting into another map.
        // Actually, we need to update the existing map.
        
        respawnQueue.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                readyToRevive.put(entry.getKey(), 0);
                return true;
            } else {
                entry.setValue(remaining);
                return false;
            }
        });

        if (!readyToRevive.isEmpty() || !respawnQueue.isEmpty()) {
            // Only mark dirty if something changed. 
            // In a tick-heavy system, we might want to be careful, but SavedData 
            // usually saves periodically or on shutdown.
            setDirty();
        }

        return readyToRevive;
    }
}
