package com.nhatbh.basedefensev2.sanctity.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PlayerReviveStateSyncPacket {
    private final int entityId;
    private final int knockedDownTimer;
    private final boolean isKnockedDown;
    private final boolean wantsRevive;
    private final int rescueProgress;
    private final BlockPos deathPos;

    public PlayerReviveStateSyncPacket(int entityId, int knockedDownTimer, boolean isKnockedDown, boolean wantsRevive, int rescueProgress, BlockPos deathPos) {
        this.entityId = entityId;
        this.knockedDownTimer = knockedDownTimer;
        this.isKnockedDown = isKnockedDown;
        this.wantsRevive = wantsRevive;
        this.rescueProgress = rescueProgress;
        this.deathPos = deathPos;
    }

    public PlayerReviveStateSyncPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.knockedDownTimer = buf.readInt();
        this.isKnockedDown = buf.readBoolean();
        this.wantsRevive = buf.readBoolean();
        this.rescueProgress = buf.readInt();
        this.deathPos = buf.readBoolean() ? buf.readBlockPos() : null;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeInt(knockedDownTimer);
        buf.writeBoolean(isKnockedDown);
        buf.writeBoolean(wantsRevive);
        buf.writeInt(rescueProgress);
        buf.writeBoolean(deathPos != null);
        if (deathPos != null) buf.writeBlockPos(deathPos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ClientReviveData.update(entityId, knockedDownTimer, isKnockedDown, wantsRevive, rescueProgress, deathPos);
        });
        return true;
    }
}
