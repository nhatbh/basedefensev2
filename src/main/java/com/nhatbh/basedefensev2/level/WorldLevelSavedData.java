package com.nhatbh.basedefensev2.level;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class WorldLevelSavedData extends SavedData {
    private static final String DATA_NAME = "basedefensev2_world_level";

    private int worldLevel = 0;

    public WorldLevelSavedData() {
    }

    public static WorldLevelSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) overworld = level;
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(WorldLevelSavedData::load, WorldLevelSavedData::new, DATA_NAME);
    }

    public static WorldLevelSavedData load(CompoundTag tag) {
        WorldLevelSavedData data = new WorldLevelSavedData();
        data.worldLevel = tag.getInt("WorldLevel");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("WorldLevel", worldLevel);
        return tag;
    }

    public int getWorldLevel() {
        return worldLevel;
    }

    public void setWorldLevel(int worldLevel) {
        this.worldLevel = Math.max(0, worldLevel);
        setDirty();
    }

    public int incrementWorldLevel() {
        this.worldLevel++;
        setDirty();
        return this.worldLevel;
    }
}
