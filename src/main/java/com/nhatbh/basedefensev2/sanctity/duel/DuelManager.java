package com.nhatbh.basedefensev2.sanctity.duel;

import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DuelManager {
    private static final long REQUEST_TIMEOUT = 60000; // 60 seconds
    
    // Target UUID -> Set of Pending Request Source UUIDs
    private static final Map<UUID, Map<UUID, Long>> pendingRequests = new ConcurrentHashMap<>();
    
    // Player UUID -> Set of Opponent UUIDs (Symmetric)
    private static final Map<UUID, Set<UUID>> activeDuels = new ConcurrentHashMap<>();

    public static void proposeDuel(ServerPlayer proposer, ServerPlayer target) {
        UUID pId = proposer.getUUID();
        UUID tId = target.getUUID();
        
        pendingRequests.computeIfAbsent(tId, k -> new ConcurrentHashMap<>()).put(pId, System.currentTimeMillis());
    }

    public static boolean hasRequest(ServerPlayer target, ServerPlayer proposer) {
        Map<UUID, Long> requests = pendingRequests.get(target.getUUID());
        if (requests == null) return false;
        
        Long timestamp = requests.get(proposer.getUUID());
        if (timestamp == null) return false;
        
        if (System.currentTimeMillis() - timestamp > REQUEST_TIMEOUT) {
            requests.remove(proposer.getUUID());
            return false;
        }
        
        return true;
    }

    public static void acceptDuel(ServerPlayer acceptor, ServerPlayer proposer) {
        UUID aId = acceptor.getUUID();
        UUID pId = proposer.getUUID();
        
        Map<UUID, Long> requests = pendingRequests.get(aId);
        if (requests != null) requests.remove(pId);
        
        activeDuels.computeIfAbsent(aId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(pId);
        activeDuels.computeIfAbsent(pId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(aId);
    }

    public static void declineDuel(ServerPlayer identifier, ServerPlayer proposer) {
        Map<UUID, Long> requests = pendingRequests.get(identifier.getUUID());
        if (requests != null) requests.remove(proposer.getUUID());
    }

    public static boolean isInDuel(UUID player1, UUID player2) {
        Set<UUID> opponents = activeDuels.get(player1);
        return opponents != null && opponents.contains(player2);
    }

    public static void endDuel(UUID player1, UUID player2) {
        Set<UUID> opp1 = activeDuels.get(player1);
        if (opp1 != null) opp1.remove(player2);
        
        Set<UUID> opp2 = activeDuels.get(player2);
        if (opp2 != null) opp2.remove(player1);
    }
}
