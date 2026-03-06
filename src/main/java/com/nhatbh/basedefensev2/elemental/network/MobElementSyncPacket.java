package com.nhatbh.basedefensev2.elemental.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MobElementSyncPacket {
    public final int entityId;
    public final String elementTypeStr;

    public MobElementSyncPacket(int entityId, String elementTypeStr) {
        this.entityId = entityId;
        this.elementTypeStr = elementTypeStr;
    }

    public MobElementSyncPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.elementTypeStr = buf.readUtf(32767);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeUtf(elementTypeStr);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            boolean isClient = context.getDirection().getReceptionSide() == LogicalSide.CLIENT;
            if (isClient) {
                ClientElementHandler.handleSync(this);
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
