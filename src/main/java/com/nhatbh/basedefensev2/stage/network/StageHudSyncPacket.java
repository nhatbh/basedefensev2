package com.nhatbh.basedefensev2.stage.network;

import com.nhatbh.basedefensev2.stage.client.ClientStageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from the server to ALL connected clients every 10 ticks.
 * Carries the full HUD snapshot so clients can render the Wave Defense HUD
 * regardless of which dimension they are currently in.
 */
public class StageHudSyncPacket {

    public final boolean active;
    public final String stageState;   // "" when inactive
    public final String waveState;    // "" when inactive / no wave
    public final int currentWaveIndex;
    public final int maxWaves;
    public final int enemiesRemaining;
    public final int totalEnemiesInWave;
    public final int stageTicks;
    public final float waveTimerRatio; // -1 = no time limit
    public final int waveRemainingTicks; // ticks left in current wave
    public final int stageRemainingTicks; // ticks left in current stage
    public final int idleTicks;       // ticks until next stage, or -1

    public StageHudSyncPacket(boolean active, String stageState, String waveState,
                               int currentWaveIndex, int maxWaves,
                               int enemiesRemaining, int totalEnemiesInWave,
                               int stageTicks, int stageRemainingTicks,
                               float waveTimerRatio,
                               int waveRemainingTicks,
                               int idleTicks) {
        this.active = active;
        this.stageState = stageState;
        this.waveState = waveState;
        this.currentWaveIndex = currentWaveIndex;
        this.maxWaves = maxWaves;
        this.enemiesRemaining = enemiesRemaining;
        this.totalEnemiesInWave = totalEnemiesInWave;
        this.stageTicks = stageTicks;
        this.stageRemainingTicks = stageRemainingTicks;
        this.waveTimerRatio = waveTimerRatio;
        this.waveRemainingTicks = waveRemainingTicks;
        this.idleTicks = idleTicks;
    }

    /** Decode constructor (called by Forge on the receiving side). */
    public StageHudSyncPacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
        this.stageState = buf.readUtf(32);
        this.waveState = buf.readUtf(32);
        this.currentWaveIndex = buf.readInt();
        this.maxWaves = buf.readInt();
        this.enemiesRemaining = buf.readInt();
        this.totalEnemiesInWave = buf.readInt();
        this.stageTicks = buf.readInt();
        this.stageRemainingTicks = buf.readInt();
        this.waveTimerRatio = buf.readFloat();
        this.waveRemainingTicks = buf.readInt();
        this.idleTicks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeUtf(stageState, 32);
        buf.writeUtf(waveState, 32);
        buf.writeInt(currentWaveIndex);
        buf.writeInt(maxWaves);
        buf.writeInt(enemiesRemaining);
        buf.writeInt(totalEnemiesInWave);
        buf.writeInt(stageTicks);
        buf.writeInt(stageRemainingTicks);
        buf.writeFloat(waveTimerRatio);
        buf.writeInt(waveRemainingTicks);
        buf.writeInt(idleTicks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientStageData.handleSync(StageHudSyncPacket.this))
        );
        ctx.setPacketHandled(true);
        return true;
    }
}
