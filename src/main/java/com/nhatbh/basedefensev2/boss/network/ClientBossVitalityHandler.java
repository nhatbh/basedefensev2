package com.nhatbh.basedefensev2.boss.network;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public class ClientBossVitalityHandler {
    public static void handleSync(BossVitalitySyncPacket packet) {
        if (Minecraft.getInstance().level != null) {
            Entity entity = Minecraft.getInstance().level.getEntity(packet.entityId);
            if (entity != null) {
                if (packet.bossId != null && !packet.bossId.isEmpty()) {
                    entity.getPersistentData().putString("bdv2_boss_id", packet.bossId);
                }
                entity.getPersistentData().putInt("BossPhaseIndex", packet.phaseIndex);
                entity.getPersistentData().putDouble("bdv2_client_cur_vitality", packet.currentVitality);
                entity.getPersistentData().putDouble("bdv2_client_max_vitality", packet.maxVitality);
                entity.getPersistentData().putInt("bdv2_client_corrosion_hits", packet.corrosionHits);
            }
        }
    }
}
