package com.nhatbh.basedefensev2.sanctity.network;

import com.nhatbh.basedefensev2.sanctity.data.ReviveStateProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RescueRequestPacket {
    private final int targetId;
    private final boolean starting;

    public RescueRequestPacket(int targetId, boolean starting) {
        this.targetId = targetId;
        this.starting = starting;
    }

    public RescueRequestPacket(FriendlyByteBuf buf) {
        this.targetId = buf.readInt();
        this.starting = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(targetId);
        buf.writeBoolean(starting);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer rescuer = context.getSender();
            if (rescuer != null) {
                rescuer.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                    if (starting) {
                        state.setRescueTargetId(targetId);
                        
                        // Also set target's rescuer UUID
                        Player target = (Player) rescuer.level().getEntity(targetId);
                        if (target != null) {
                            target.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(targetState -> {
                                targetState.setRescuerUUID(rescuer.getUUID());
                            });
                        }
                    } else {
                        if (state.getRescueTargetId() == targetId) {
                            state.setRescueTargetId(-1);
                            
                            // Also clear target's rescuer UUID
                            Player target = (Player) rescuer.level().getEntity(targetId);
                            if (target != null) {
                                target.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(targetState -> {
                                    if (rescuer.getUUID().equals(targetState.getRescuerUUID())) {
                                        targetState.resetRescue();
                                    }
                                });
                            }
                        }
                    }
                });
            }
        });
        return true;
    }
}
