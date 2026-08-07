package com.nhatbh.basedefensev2.level.network;

import com.nhatbh.basedefensev2.level.MobLevelData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MobLevelSyncPacket {
    public final int entityId;
    public final int level;

    public MobLevelSyncPacket(int entityId, int level) {
        this.entityId = entityId;
        this.level = level;
    }

    public MobLevelSyncPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.level = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeInt(level);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    if (Minecraft.getInstance().level != null) {
                        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
                        if (entity instanceof LivingEntity living) {
                            MobLevelData.setLevel(living, level);
                        }
                    }
                });
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
