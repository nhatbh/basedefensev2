package com.nhatbh.basedefensev2.stage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nhatbh.basedefensev2.BaseDefenseMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Renders the Wave Defense Stage and Wave progress at the top-right of the screen.
 * Data is supplied by {@link ClientStageData}, which is updated by
 * {@link com.nhatbh.basedefensev2.stage.network.StageHudSyncPacket} every 10 ticks.
 */
@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WaveDefenseHUD {

    private static final int BAR_WIDTH    = 150;
    private static final int BAR_HEIGHT   = 5;
    private static final int MARGIN_TOP   = 10;
    private static final int MARGIN_RIGHT = 10;

    // ── Text cache ───────────────────────────────────────────────────────────

    private static String cachedStageText    = "";
    private static String cachedWaveText     = "";
    private static String cachedTimerText     = "";
    private static String cachedProgressText = "";
    private static String cachedIdleText     = "";

    private static int cachedStageTextWidth    = 0;
    private static int cachedWaveTextWidth     = 0;
    private static int cachedTimerTextWidth    = 0;
    private static int cachedProgressTextWidth = 0;
    private static int cachedIdleTextWidth     = 0;

    // ── State trackers (detect changes to avoid spamming mc.font.width) ─────

    private static String lastStageState    = "";
    private static String lastWaveState     = "";
    private static int    lastWaveIndex     = -1;
    private static int    lastMaxWaves      = -1;
    private static int    lastEnemies       = -1;
    private static int    lastTotalEnemies  = -1;
    private static int    lastStageSeconds  = -1;
    private static int    lastStageRemSeconds = -1;
    private static int    lastWaveRemSeconds = -1;
    private static float  lastWaveRatio     = -1f;
    private static int    lastIdleSeconds   = -1;

    // ── Registration ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(
                VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(),
                "wave_defense_hud",
                WaveDefenseHUD::render
        );
    }

    // ── Render entry point ───────────────────────────────────────────────────

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.renderDebug || mc.screen != null) return;

        updateCaches(mc);

        // The right boundary anchor — all text and the bar flush right to this X
        int anchorX  = width - MARGIN_RIGHT;
        int currentY = MARGIN_TOP;

        if (!ClientStageData.isActive()) {
            if (ClientStageData.getIdleTicks() >= 0) {
                graphics.drawString(mc.font, cachedIdleText,
                        anchorX - cachedIdleTextWidth, currentY, 0xAAAAAA, true);
            }
            return;
        }

        if (ClientStageData.getStageState().equals("ACTIVE")) {
            // Wave info line
            graphics.drawString(mc.font, cachedWaveText,
                    anchorX - cachedWaveTextWidth, currentY, 0xFFFFFF, true);
            currentY += 12;

            // Timer Text Line
            if (!cachedTimerText.isEmpty()) {
                graphics.drawString(mc.font, cachedTimerText,
                        anchorX - cachedTimerTextWidth, currentY, 0xAAAAAA, true);
                currentY += 12;
            }

            // Enemy / status line
            graphics.drawString(mc.font, cachedProgressText,
                    anchorX - cachedProgressTextWidth, currentY, 0xFFFFAA, true);
            currentY += 10; // Spaced correctly before the bar

            // Enemy Progress Bar (empties left to right)
            renderEnemyProgressBar(graphics, anchorX, currentY);
        } else {
            // Stage header (coloured by stage state) for WARMUP / SCAVENGE / etc.
            graphics.drawString(mc.font, cachedStageText,
                    anchorX - cachedStageTextWidth, currentY, getStageColor(), true);
            currentY += 12;

            // Optional: You could show ClientStageData.getWaveState() but the user 
            // specifically asked for wave information ONLY when active.
        }
    }

    // ── Cache update ─────────────────────────────────────────────────────────

    private static void updateCaches(Minecraft mc) {
        if (!ClientStageData.isActive()) {
            int idleSec = ClientStageData.getIdleSeconds();
            if (idleSec != lastIdleSeconds) {
                lastIdleSeconds = idleSec;
                if (idleSec < 0) {
                    cachedIdleText = "No stages remaining";
                } else {
                    int m = idleSec / 60;
                    int s = idleSec % 60;
                    cachedIdleText = "Next Stage in: " + m + ":" + (s < 10 ? "0" + s : s);
                }
                cachedIdleTextWidth = mc.font.width(cachedIdleText);
            }
            return;
        }

        String currentStageState = ClientStageData.getStageState();
        int    stageSeconds      = ClientStageData.getStageSecondsElapsed();
        int    stageRemSeconds   = ClientStageData.getStageRemainingSeconds();

        if (!currentStageState.equals(lastStageState) || stageSeconds != lastStageSeconds || stageRemSeconds != lastStageRemSeconds) {
            lastStageState   = currentStageState;
            lastStageSeconds = stageSeconds;
            lastStageRemSeconds = stageRemSeconds;

            String timeStr;
            if (currentStageState.equals("WARMUP") || currentStageState.equals("SCAVENGE")) {
                int m = stageRemSeconds / 60;
                int s = stageRemSeconds % 60;
                timeStr = m + ":" + (s < 10 ? "0" + s : s);
            } else {
                int m = stageSeconds / 60;
                int s = stageSeconds % 60;
                timeStr = m + ":" + (s < 10 ? "0" + s : s);
            }

            cachedStageText      = "Stage: " + currentStageState + " (" + timeStr + ")";
            cachedStageTextWidth = mc.font.width(cachedStageText);
        }

        String currentWaveState = ClientStageData.getWaveState();
        int    waveIndex        = ClientStageData.getCurrentWaveIndex();
        int    maxWaves         = ClientStageData.getMaxWaves();

        if (!currentWaveState.equals(lastWaveState) || waveIndex != lastWaveIndex || maxWaves != lastMaxWaves) {
            lastWaveState  = currentWaveState;
            lastWaveIndex  = waveIndex;
            lastMaxWaves   = maxWaves;

            cachedWaveText      = "Wave " + (waveIndex + 1) + "/" + maxWaves;
            if (!currentWaveState.equals("COMBAT")) {
                String stateDisplay = currentWaveState.equals("WAITING_NEXT_WAVE") ? "NEXT WAVE" : currentWaveState;
                cachedWaveText += " (" + stateDisplay + ")";
            }
            cachedWaveTextWidth = mc.font.width(cachedWaveText);
        }

        int waveRemSeconds = ClientStageData.getWaveRemainingTicks() > 0 ? ClientStageData.getWaveRemainingSeconds() : -1;
        float waveRatio    = ClientStageData.getWaveTimerRatio();

        if (waveRemSeconds != lastWaveRemSeconds || waveRatio != lastWaveRatio) {
            lastWaveRemSeconds = waveRemSeconds;
            lastWaveRatio      = waveRatio;

            if (waveRatio < 0) {
                cachedTimerText = "Time: Unlimited";
            } else {
                int m = waveRemSeconds / 60;
                int s = waveRemSeconds % 60;
                cachedTimerText = "Time: " + m + ":" + (s < 10 ? "0" + s : s);
            }
            cachedTimerTextWidth = mc.font.width(cachedTimerText);
        }

        int enemies      = ClientStageData.getEnemiesRemaining();
        int totalEnemies = ClientStageData.getTotalEnemiesInWave();

        if (enemies != lastEnemies || totalEnemies != lastTotalEnemies) {
            lastEnemies      = enemies;
            lastTotalEnemies = totalEnemies;

            if (currentWaveState.equals("WARMUP") || currentWaveState.equals("SPAWNING")) {
                cachedProgressText = "Prepare for battle...";
            } else if (currentWaveState.equals("WAITING_NEXT_WAVE")) {
                cachedProgressText = "Next wave in: " + ClientStageData.getWaveRemainingSeconds() + "s";
            } else if (currentStageState.equals("SCAVENGE")) {
                cachedProgressText = "Collect your loot!";
            } else {
                cachedProgressText = "Enemies: " + enemies + " / " + totalEnemies;
            }
            cachedProgressTextWidth = mc.font.width(cachedProgressText);
        }
    }

    // ── Enemy Progress Bar ────────────────────────────────────────────────────
    
    /**
     * Renders a bar representing enemies remaining. 
     * Empties from LEFT to RIGHT (filled part stays on the right).
     */
    private static void renderEnemyProgressBar(GuiGraphics graphics, int anchorX, int y) {
        int enemies      = ClientStageData.getEnemiesRemaining();
        int totalEnemies = ClientStageData.getTotalEnemiesInWave();
        
        if (totalEnemies <= 0) return;

        float ratio     = (float) enemies / totalEnemies;
        float clamped   = Math.max(0.0f, Math.min(1.0f, ratio));
        int filledWidth = (int) (BAR_WIDTH * clamped);

        int x = anchorX - BAR_WIDTH;

        RenderSystem.enableBlend();

        // Background (semi-transparent black)
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0x99000000);

        // Fill - Anchored to the RIGHT
        // Empty space grows from left (x), filled part starts at (x + BAR_WIDTH - filledWidth)
        if (filledWidth > 0) {
            int fillStart = x + (BAR_WIDTH - filledWidth);
            // Red/Orange/Green transition based on enemies left
            int color = 0xFF55FF55; // Default Green
            if (clamped > 0.6f) color = 0xFFFF5555; // Mostly full = Red (lots of enemies)
            else if (clamped > 0.3f) color = 0xFFFFAA00; // Orange

            graphics.fill(fillStart, y, x + BAR_WIDTH, y + BAR_HEIGHT, color);
        }

        // Border (subtle grey/white outline)
        graphics.fill(x,               y,                x + BAR_WIDTH, y + 1,              0x99FFFFFF);
        graphics.fill(x,               y + BAR_HEIGHT-1, x + BAR_WIDTH, y + BAR_HEIGHT,     0x99FFFFFF);
        graphics.fill(x,               y,                x + 1,         y + BAR_HEIGHT,     0x99FFFFFF);
        graphics.fill(x + BAR_WIDTH-1, y,                x + BAR_WIDTH, y + BAR_HEIGHT,     0x99FFFFFF);

        RenderSystem.disableBlend();
    }

    // ── Colour helper ────────────────────────────────────────────────────────

    private static int getStageColor() {
        return switch (lastStageState) {
            case "WARMUP"   -> 0xFFD700; // gold
            case "SCAVENGE" -> 0x55FF55; // green
            case "ENDED"    -> 0xAAAAAA; // grey
            default         -> 0xFF5555; // red (ACTIVE / fallback)
        };
    }
}
