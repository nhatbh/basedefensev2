package com.nhatbh.basedefensev2.sanctity.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SanctitySyncPacket {
    private final int sanctity;
    private final double grace;
    private final int maxSanctity;
    private final int maxGrace;

    public SanctitySyncPacket(int sanctity, double grace, int maxSanctity, int maxGrace) {
        this.sanctity = sanctity;
        this.grace = grace;
        this.maxSanctity = maxSanctity;
        this.maxGrace = maxGrace;
    }

    public SanctitySyncPacket(FriendlyByteBuf buf) {
        this.sanctity = buf.readInt();
        this.grace = buf.readDouble();
        this.maxSanctity = buf.readInt();
        this.maxGrace = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(sanctity);
        buf.writeDouble(grace);
        buf.writeInt(maxSanctity);
        buf.writeInt(maxGrace);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ClientSanctityData.setSanctity(sanctity);
            ClientSanctityData.setGrace(grace);
            ClientSanctityData.setMaxSanctity(maxSanctity);
            ClientSanctityData.setMaxGrace(maxGrace);
        });
        context.setPacketHandled(true);
        return true;
    }
}
