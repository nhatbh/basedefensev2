package com.nhatbh.basedefensev2.elemental.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

public class ClientElementHandler {
    public static void handleSync(MobElementSyncPacket msg) {
        @SuppressWarnings("resource")
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != null) {
            Entity entity = level.getEntity(msg.entityId);
            if (entity != null) {
                entity.getPersistentData().putString("ElementType", msg.elementTypeStr);
            }
        }
    }
}
