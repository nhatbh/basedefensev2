package com.nhatbh.basedefensev2.strength.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class EntityStrengthSyncPacket {
    public final int entityId;
    public final float currentStrength;
    public final float maxStrength;
    public final int recoveryTicks;

    public EntityStrengthSyncPacket(int entityId, float currentStrength, float maxStrength, int recoveryTicks) {
        this.entityId = entityId;
        this.currentStrength = currentStrength;
        this.maxStrength = maxStrength;
        this.recoveryTicks = recoveryTicks;
    }

    public EntityStrengthSyncPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.currentStrength = buf.readFloat();
        this.maxStrength = buf.readFloat();
        this.recoveryTicks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeFloat(currentStrength);
        buf.writeFloat(maxStrength);
        buf.writeInt(recoveryTicks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            boolean isClient = context.getDirection().getReceptionSide() == LogicalSide.CLIENT;
            if (isClient) {
                ClientStrengthHandler.handleSync(this);
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
