package com.nhatbh.basedefensev2.stage.client;

import com.nhatbh.basedefensev2.stage.network.StageHudSyncPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-side singleton that stores the latest snapshot received from
 * {@link StageHudSyncPacket}.  All fields are set atomically by the
 * packet handler on the render thread before the HUD reads them.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientStageData {

    private ClientStageData() {}

    private static boolean active = false;
    private static String stageState = "";
    private static String waveState = "";
    private static int currentWaveIndex = 0;
    private static int maxWaves = 0;
    private static int enemiesRemaining = 0;
    private static int totalEnemiesInWave = 0;
    private static int   stageTicks = 0;
    private static int   stageRemainingTicks = 0;
    private static float waveTimerRatio = -1f;
    private static int   waveRemainingTicks = 0;
    private static int   idleTicks = -1;

    // ── Packet handler ───────────────────────────────────────────────────────

    public static void handleSync(StageHudSyncPacket pkt) {
        active              = pkt.active;
        stageState          = pkt.stageState;
        waveState           = pkt.waveState;
        currentWaveIndex    = pkt.currentWaveIndex;
        maxWaves            = pkt.maxWaves;
        enemiesRemaining    = pkt.enemiesRemaining;
        totalEnemiesInWave  = pkt.totalEnemiesInWave;
        stageTicks          = pkt.stageTicks;
        stageRemainingTicks = pkt.stageRemainingTicks;
        waveTimerRatio      = pkt.waveTimerRatio;
        waveRemainingTicks  = pkt.waveRemainingTicks;
        idleTicks           = pkt.idleTicks;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public static boolean isActive()               { return active; }
    public static String  getStageState()          { return stageState; }
    public static String  getWaveState()           { return waveState; }
    public static int     getCurrentWaveIndex()    { return currentWaveIndex; }
    public static int     getMaxWaves()            { return maxWaves; }
    public static int     getEnemiesRemaining()    { return enemiesRemaining; }
    public static int     getTotalEnemiesInWave()  { return totalEnemiesInWave; }

    /** Converts the raw stage tick counter to elapsed seconds. */
    public static int     getStageSecondsElapsed() { return stageTicks / 20; }
    public static int     getStageRemainingTicks() { return stageRemainingTicks; }
    public static int     getStageRemainingSeconds() { return stageRemainingTicks / 20; }
    public static float   getWaveTimerRatio()      { return waveTimerRatio; }
    public static int     getWaveRemainingTicks()  { return waveRemainingTicks; }
    public static int     getWaveRemainingSeconds(){ return waveRemainingTicks / 20; }

    public static int     getIdleTicks()           { return idleTicks; }
    public static int     getIdleSeconds()         { return idleTicks / 20; }
}
