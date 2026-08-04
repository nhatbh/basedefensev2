package com.nhatbh.basedefensev2.sanctity.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import java.util.UUID;

public class ReviveState {
    public static final int INITIAL_KNOCKDOWN_TICKS = 12000; // 10 minutes
    public static final int MAX_SPECTATOR_TICKS = 2400; // 2 minutes (2400 ticks)

    private int knockedDownTimer = INITIAL_KNOCKDOWN_TICKS;
    private int spectatorTimer = MAX_SPECTATOR_TICKS;
    private boolean isKnockedDown = false;
    private boolean wantsRevive = false;
    private int rescueProgress = 0;
    private UUID rescuerUUID = null;
    private int rescueTargetId = -1; // Entity ID of who we are currently rescuing
    private BlockPos deathPos = null;

    public void copyFrom(ReviveState other) {
        this.knockedDownTimer = other.knockedDownTimer;
        this.spectatorTimer = other.spectatorTimer;
        this.isKnockedDown = other.isKnockedDown;
        this.wantsRevive = other.wantsRevive;
        this.rescueProgress = other.rescueProgress;
        this.rescuerUUID = other.rescuerUUID;
        this.rescueTargetId = other.rescueTargetId;
        this.deathPos = other.deathPos;
    }

    public void saveNBTData(CompoundTag nbt) {
        nbt.putInt("knockedDownTimer", knockedDownTimer);
        nbt.putInt("spectatorTimer", spectatorTimer);
        nbt.putBoolean("isKnockedDown", isKnockedDown);
        nbt.putBoolean("wantsRevive", wantsRevive);
        nbt.putInt("rescueProgress", rescueProgress);
        if (rescuerUUID != null) nbt.putUUID("rescuerUUID", rescuerUUID);
        if (deathPos != null) nbt.put("deathPos", NbtUtils.writeBlockPos(deathPos));
    }

    public void loadNBTData(CompoundTag nbt) {
        knockedDownTimer = nbt.getInt("knockedDownTimer");
        if (nbt.contains("spectatorTimer")) spectatorTimer = nbt.getInt("spectatorTimer");
        isKnockedDown = nbt.getBoolean("isKnockedDown");
        wantsRevive = nbt.getBoolean("wantsRevive");
        rescueProgress = nbt.getInt("rescueProgress");
        if (nbt.hasUUID("rescuerUUID")) rescuerUUID = nbt.getUUID("rescuerUUID");
        if (nbt.contains("deathPos")) deathPos = NbtUtils.readBlockPos(nbt.getCompound("deathPos"));
    }

    // Getters and Setters
    public int getKnockedDownTimer() { return knockedDownTimer; }
    public void setKnockedDownTimer(int ticks) { this.knockedDownTimer = ticks; }

    public int getSpectatorTimer() { return spectatorTimer; }
    public void setSpectatorTimer(int ticks) { this.spectatorTimer = ticks; }

    public boolean isKnockedDown() { return isKnockedDown; }
    public void setKnockedDown(boolean knockedDown) { isKnockedDown = knockedDown; }

    public boolean wantsRevive() { return wantsRevive; }
    public void setWantsRevive(boolean wantsRevive) { this.wantsRevive = wantsRevive; }

    public int getRescueProgress() { return rescueProgress; }
    public void setRescueProgress(int progress) { this.rescueProgress = progress; }

    public UUID getRescuerUUID() { return rescuerUUID; }
    public void setRescuerUUID(UUID uuid) { this.rescuerUUID = uuid; }

    public int getRescueTargetId() { return rescueTargetId; }
    public void setRescueTargetId(int id) { this.rescueTargetId = id; }

    public BlockPos getDeathPos() { return deathPos; }
    public void setDeathPos(BlockPos pos) { this.deathPos = pos; }
    
    public void resetRescue() {
        this.rescueProgress = 0;
        this.rescuerUUID = null;
    }
}
