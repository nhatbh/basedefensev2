package com.nhatbh.basedefensev2.boss.network;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.skills.SkillIndicatorData;
import com.nhatbh.basedefensev2.elemental.ElementType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class EntitySkillSyncPacket {
    public final SkillIndicatorData data;
    public final boolean clear;

    public EntitySkillSyncPacket(SkillIndicatorData data, boolean clear) {
        this.data = data;
        this.clear = clear;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(data.entityId);
        buf.writeUtf(data.stepId);
        buf.writeInt(data.tickInStep);
        buf.writeInt(data.totalDuration);
        buf.writeEnum(data.counterType);
        buf.writeInt(data.counterWindowStart);
        buf.writeInt(data.counterWindowEnd);
        
        boolean hasDir = data.counterDirection != null;
        buf.writeBoolean(hasDir);
        if (hasDir) {
            buf.writeDouble(data.counterDirection.x);
            buf.writeDouble(data.counterDirection.y);
            buf.writeDouble(data.counterDirection.z);
        }

        boolean hasElement = data.magicElement != null;
        buf.writeBoolean(hasElement);
        if (hasElement) {
            buf.writeEnum(data.magicElement);
        }
        
        buf.writeBoolean(data.isParry);
        buf.writeBoolean(clear);
    }

    public EntitySkillSyncPacket(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        String stepId = buf.readUtf();
        int tickInStep = buf.readInt();
        int totalDuration = buf.readInt();
        ActiveSequence.CounterType counterType = buf.readEnum(ActiveSequence.CounterType.class);
        int counterWindowStart = buf.readInt();
        int counterWindowEnd = buf.readInt();
        
        Vec3 counterDirection = null;
        if (buf.readBoolean()) {
            counterDirection = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        ElementType magicElement = null;
        if (buf.readBoolean()) {
            magicElement = buf.readEnum(ElementType.class);
        }
        
        boolean isParry = buf.readBoolean();
        
        this.data = new SkillIndicatorData(
            entityId, stepId, tickInStep, totalDuration, counterType, counterWindowStart, counterWindowEnd,
            counterDirection, magicElement, isParry
        );
        this.clear = buf.readBoolean();
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            com.nhatbh.basedefensev2.boss.client.ClientSkillHandler.handleSync(this.data, this.clear);
        });
        context.setPacketHandled(true);
        return true;
    }
}
