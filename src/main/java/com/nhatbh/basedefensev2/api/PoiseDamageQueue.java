package com.nhatbh.basedefensev2.api;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.*;

public class PoiseDamageQueue {

    public static class PoiseDamageRequest {
        public final LivingEntity target;
        public final float poiseAmount;
        public final float vitalityAmount;
        public final LivingEntity attacker;
        public final DamageSource source;
        public final boolean enableAttributeScaling;
        public final String sourceMod;
        public final int priority;
        @Nullable public final String ammoType;

        public PoiseDamageRequest(LivingEntity target, float poiseAmount, float vitalityAmount,
                                 @Nullable LivingEntity attacker, @Nullable DamageSource source,
                                 boolean enableAttributeScaling, @Nullable String sourceMod, int priority) {
            this(target, poiseAmount, vitalityAmount, attacker, source, enableAttributeScaling, sourceMod, priority, null);
        }

        public PoiseDamageRequest(LivingEntity target, float poiseAmount, float vitalityAmount,
                                 @Nullable LivingEntity attacker, @Nullable DamageSource source,
                                 boolean enableAttributeScaling, @Nullable String sourceMod, int priority,
                                 @Nullable String ammoType) {
            this.target = target;
            this.poiseAmount = poiseAmount;
            this.vitalityAmount = vitalityAmount;
            this.attacker = attacker;
            this.source = source;
            this.enableAttributeScaling = enableAttributeScaling;
            this.sourceMod = (sourceMod != null && !sourceMod.isEmpty()) ? sourceMod : "BaseDefense";
            this.priority = priority;
            this.ammoType = ammoType;
        }
    }

    private static final Map<String, List<PoiseDamageRequest>> QUEUED_ATTACKS = new HashMap<>();

    public static String generateAttackId(LivingEntity entity, @Nullable DamageSource source, @Nullable LivingEntity attacker) {
        long gameTime = entity.level().getGameTime();
        String attackMsgId = source != null ? source.getMsgId() : "direct";
        int directEntityId = (source != null && source.getDirectEntity() != null) ? source.getDirectEntity().getId() : (attacker != null ? attacker.getId() : 0);
        return gameTime + "_" + entity.getId() + "_" + attackMsgId + "_" + directEntityId;
    }

    public static synchronized void queueRequest(String attackId, PoiseDamageRequest request) {
        QUEUED_ATTACKS.computeIfAbsent(attackId, k -> new ArrayList<>()).add(request);
    }

    public static synchronized List<PoiseDamageRequest> getQueuedRequests(String attackId) {
        return QUEUED_ATTACKS.get(attackId);
    }

    @Nullable
    public static synchronized PoiseDamageRequest resolveWinningRequest(String attackId) {
        List<PoiseDamageRequest> requests = QUEUED_ATTACKS.remove(attackId);
        if (requests == null || requests.isEmpty()) return null;

        PoiseDamageRequest winner = null;
        for (PoiseDamageRequest req : requests) {
            if (winner == null || req.priority > winner.priority) {
                winner = req;
            }
        }
        return winner;
    }

    public static synchronized List<String> getAttackIdsForEntity(LivingEntity entity) {
        List<String> matching = new ArrayList<>();
        int entityId = entity.getId();
        long gameTime = entity.level().getGameTime();
        for (String attackId : QUEUED_ATTACKS.keySet()) {
            if (attackId.contains("_" + entityId + "_") && attackId.startsWith(gameTime + "_")) {
                matching.add(attackId);
            }
        }
        return matching;
    }

    public static synchronized void clearOldRequests(long currentGameTime) {
        QUEUED_ATTACKS.keySet().removeIf(key -> {
            try {
                long time = Long.parseLong(key.split("_")[0]);
                return time < currentGameTime - 5;
            } catch (Exception e) {
                return true;
            }
        });
    }
}
