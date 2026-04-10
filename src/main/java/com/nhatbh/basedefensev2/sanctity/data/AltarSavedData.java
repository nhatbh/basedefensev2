package com.nhatbh.basedefensev2.sanctity.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class AltarSavedData extends SavedData {
    private static final String DATA_NAME = "basedefense_altar_data";

    private int sanctity = com.nhatbh.basedefensev2.config.SanctityConfig.data.maxSanctity;
    private double grace = 0;

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
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Sanctity", sanctity);
        tag.putDouble("Grace", grace);
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
}
