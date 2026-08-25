package com.nhatbh.basedefensev2.boss.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BossVitalitySyncPacket {
    public final int entityId;
    public final String bossId;
    public final double currentVitality;
    public final double maxVitality;
    public final int corrosionHits;
    public final int phaseIndex;

    public BossVitalitySyncPacket(int entityId, String bossId, double currentVitality, double maxVitality, int corrosionHits, int phaseIndex) {
        this.entityId = entityId;
        this.bossId = bossId != null ? bossId : "";
        this.currentVitality = currentVitality;
        this.maxVitality = maxVitality;
        this.corrosionHits = corrosionHits;
        this.phaseIndex = phaseIndex;
    }

    public BossVitalitySyncPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.bossId = buf.readUtf();
        this.currentVitality = buf.readDouble();
        this.maxVitality = buf.readDouble();
        this.corrosionHits = buf.readInt();
        this.phaseIndex = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeUtf(bossId);
        buf.writeDouble(currentVitality);
        buf.writeDouble(maxVitality);
        buf.writeInt(corrosionHits);
        buf.writeInt(phaseIndex);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> ClientBossVitalityHandler.handleSync(BossVitalitySyncPacket.this));
        });
        context.setPacketHandled(true);
        return true;
    }
}
