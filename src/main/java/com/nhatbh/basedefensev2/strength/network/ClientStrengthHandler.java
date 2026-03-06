package com.nhatbh.basedefensev2.strength.network;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.nhatbh.basedefensev2.strength.EntityStrengthData;

public class ClientStrengthHandler {
    public static void handleSync(EntityStrengthSyncPacket msg) {
        if (Minecraft.getInstance().level != null) {
            Entity entity = Minecraft.getInstance().level.getEntity(msg.entityId);
            if (entity instanceof LivingEntity living) {
                EntityStrengthData data = EntityStrengthData.get(living);
                if (data != null) {
                    data.currentStrength = msg.currentStrength;
                    data.maxStrength = msg.maxStrength;
                    data.recoveryTicks = msg.recoveryTicks;
                    data.save(living);
                } else {
                    EntityStrengthData newData = new EntityStrengthData(
                        msg.maxStrength, msg.currentStrength, 0f, false, msg.recoveryTicks
                    );
                    newData.save(living);
                }
            }
        }
    }
}
