package com.nhatbh.basedefensev2.sanctity.network;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;

public class ClientReviveData {
    private static final Map<Integer, ReviveStateData> playerStates = new HashMap<>();
    private static int localPlayerId = -1;

    public static class ReviveStateData {
        public final int knockedDownTimer;
        public final int spectatorTimer;
        public final boolean isKnockedDown;
        public final boolean wantsRevive;
        public final int rescueProgress;
        public final BlockPos deathPos;

        public ReviveStateData(int knockedDownTimer, int spectatorTimer, boolean isKnockedDown, boolean wantsRevive, int rescueProgress, BlockPos deathPos) {
            this.knockedDownTimer = knockedDownTimer;
            this.spectatorTimer = spectatorTimer;
            this.isKnockedDown = isKnockedDown;
            this.wantsRevive = wantsRevive;
            this.rescueProgress = rescueProgress;
            this.deathPos = deathPos;
        }
    }

    public static void setLocalPlayerId(int id) {
        localPlayerId = id;
    }

    public static void update(int entityId, int knockedDownTimer, int spectatorTimer, boolean isKnockedDown, boolean wantsRevive, int rescueProgress, BlockPos deathPos) {
        playerStates.put(entityId, new ReviveStateData(knockedDownTimer, spectatorTimer, isKnockedDown, wantsRevive, rescueProgress, deathPos));
    }

    public static ReviveStateData get(int entityId) {
        return playerStates.get(entityId);
    }

    public static ReviveStateData getLocal() {
        return playerStates.get(localPlayerId);
    }

    public static int getKnockedDownTimer() { 
        ReviveStateData data = getLocal();
        return data != null ? data.knockedDownTimer : 0; 
    }

    public static int getSpectatorTimer() { 
        ReviveStateData data = getLocal();
        return data != null ? data.spectatorTimer : 0; 
    }
    
    public static boolean isKnockedDown() { 
        ReviveStateData data = getLocal();
        return data != null && data.isKnockedDown; 
    }
    
    public static boolean wantsRevive() { 
        ReviveStateData data = getLocal();
        return data != null && data.wantsRevive; 
    }
    
    public static int getRescueProgress() { 
        ReviveStateData data = getLocal();
        return data != null ? data.rescueProgress : 0; 
    }
    
    public static BlockPos getDeathPos() { 
        ReviveStateData data = getLocal();
        return data != null ? data.deathPos : null; 
    }
}
