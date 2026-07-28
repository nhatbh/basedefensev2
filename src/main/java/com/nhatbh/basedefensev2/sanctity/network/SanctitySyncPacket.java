package com.nhatbh.basedefensev2.sanctity.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SanctitySyncPacket {
    private final int sanctity;
    private final double grace;
    private final int maxSanctity;
    private final int maxGrace;
    private final int retriesUsed;
    private final int maxWorldRetries;

    public SanctitySyncPacket(int sanctity, double grace, int maxSanctity, int maxGrace, int retriesUsed, int maxWorldRetries) {
        this.sanctity = sanctity;
        this.grace = grace;
        this.maxSanctity = maxSanctity;
        this.maxGrace = maxGrace;
        this.retriesUsed = retriesUsed;
        this.maxWorldRetries = maxWorldRetries;
    }

    public SanctitySyncPacket(FriendlyByteBuf buf) {
        this.sanctity = buf.readInt();
        this.grace = buf.readDouble();
        this.maxSanctity = buf.readInt();
        this.maxGrace = buf.readInt();
        this.retriesUsed = buf.readInt();
        this.maxWorldRetries = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(sanctity);
        buf.writeDouble(grace);
        buf.writeInt(maxSanctity);
        buf.writeInt(maxGrace);
        buf.writeInt(retriesUsed);
        buf.writeInt(maxWorldRetries);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ClientSanctityData.setSanctity(sanctity);
            ClientSanctityData.setGrace(grace);
            ClientSanctityData.setMaxSanctity(maxSanctity);
            ClientSanctityData.setMaxGrace(maxGrace);
            ClientSanctityData.setRetriesUsed(retriesUsed);
            ClientSanctityData.setMaxWorldRetries(maxWorldRetries);
        });
        context.setPacketHandled(true);
        return true;
    }
}
