package com.nhatbh.basedefensev2.sanctity.network;

import com.nhatbh.basedefensev2.sanctity.data.ReviveStateProvider;
import com.nhatbh.basedefensev2.sanctity.events.SanctityEventHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class GiveUpPacket {

    public GiveUpPacket() {
    }

    public GiveUpPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                    if (state.isKnockedDown()) {
                        SanctityEventHandler.enterSpectator(player, state);
                    }
                });
            }
        });
        return true;
    }
}
