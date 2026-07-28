package com.nhatbh.basedefensev2.stage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.boss.client.BossInfoHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Renders the Wave Defense Stage and Wave progress on the LEFT side of the screen directly under the Boss HUD.
 * Footprint is kept compact to maximize visibility.
 */
@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WaveDefenseHUD {

    private static final int BAR_WIDTH   = 110;
    private static final int BAR_HEIGHT  = 4;
    private static final int MARGIN_LEFT = 10;

    // ── Text cache ───────────────────────────────────────────────────────────
    private static String cachedHeaderLine = "";
    private static String cachedSubLine    = "";
    private static String cachedIdleText   = "";

    // ── State trackers ───────────────────────────────────────────────────────
    private static String lastStageState    = "";
    private static String lastWaveState     = "";
    private static int    lastWaveIndex     = -1;
    private static int    lastMaxWaves      = -1;
    private static int    lastEnemies       = -1;
    private static int    lastTotalEnemies  = -1;
    private static int    lastWaveRemSec    = -1;
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

        updateCaches();

        int startX = MARGIN_LEFT;
        int currentY = BossInfoHUD.getBottomY() + 4;

        if (!ClientStageData.isActive()) {
            if (ClientStageData.getIdleTicks() >= 0 && !cachedIdleText.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().scale(0.7f, 0.7f, 1.0f);
                graphics.drawString(mc.font, cachedIdleText, (int) (startX / 0.7f), (int) (currentY / 0.7f), 0xAAAAAA, true);
                graphics.pose().popPose();
            }
            return;
        }

        if (ClientStageData.getStageState().equals("ACTIVE")) {
            // Line 1: Header (Wave X/Y • Enemies: A/B)
            graphics.pose().pushPose();
            graphics.pose().scale(0.75f, 0.75f, 1.0f);
            graphics.drawString(mc.font, cachedHeaderLine, (int) (startX / 0.75f), (int) (currentY / 0.75f), 0xFFFFFF, true);
            graphics.pose().popPose();

            currentY += (int) (mc.font.lineHeight * 0.75f) + 1;

            // Line 2: Timer
            if (!cachedSubLine.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().scale(0.7f, 0.7f, 1.0f);
                graphics.drawString(mc.font, cachedSubLine, (int) (startX / 0.7f), (int) (currentY / 0.7f), 0xFFFFAA, true);
                graphics.pose().popPose();

                currentY += (int) (mc.font.lineHeight * 0.7f) + 2;
            }

            // Compact Enemy Progress Bar
            renderEnemyProgressBar(graphics, startX, currentY);
        } else {
            // Stage WARMUP / SCAVENGE Header
            graphics.pose().pushPose();
            graphics.pose().scale(0.75f, 0.75f, 1.0f);
            graphics.drawString(mc.font, cachedHeaderLine, (int) (startX / 0.75f), (int) (currentY / 0.75f), getStageColor(), true);
            graphics.pose().popPose();
        }
    }

    // ── Cache update ─────────────────────────────────────────────────────────
    private static void updateCaches() {
        if (!ClientStageData.isActive()) {
            int idleSec = ClientStageData.getIdleSeconds();
            if (idleSec != lastIdleSeconds) {
                lastIdleSeconds = idleSec;
                if (idleSec < 0) {
                    cachedIdleText = "No stages remaining";
                } else {
                    int m = idleSec / 60;
                    int s = idleSec % 60;
                    cachedIdleText = "Next Stage: " + m + ":" + (s < 10 ? "0" + s : s);
                }
            }
            return;
        }

        String stageState = ClientStageData.getStageState();
        String waveState  = ClientStageData.getWaveState();
        int waveIndex     = ClientStageData.getCurrentWaveIndex();
        int maxWaves      = ClientStageData.getMaxWaves();
        int enemies       = ClientStageData.getEnemiesRemaining();
        int totalEnemies  = ClientStageData.getTotalEnemiesInWave();
        int waveRemSec    = ClientStageData.getWaveRemainingTicks() > 0 ? ClientStageData.getWaveRemainingSeconds() : -1;

        if (!stageState.equals(lastStageState) || !waveState.equals(lastWaveState) 
                || waveIndex != lastWaveIndex || maxWaves != lastMaxWaves 
                || enemies != lastEnemies || totalEnemies != lastTotalEnemies || waveRemSec != lastWaveRemSec) {

            lastStageState   = stageState;
            lastWaveState    = waveState;
            lastWaveIndex    = waveIndex;
            lastMaxWaves     = maxWaves;
            lastEnemies      = enemies;
            lastTotalEnemies = totalEnemies;
            lastWaveRemSec   = waveRemSec;

            if (!stageState.equals("ACTIVE")) {
                int stageRemSec = ClientStageData.getStageRemainingSeconds();
                int m = stageRemSec / 60;
                int s = stageRemSec % 60;
                cachedHeaderLine = "STAGE " + stageState + " (" + m + ":" + (s < 10 ? "0" + s : s) + ")";
                cachedSubLine = "";
            } else {
                cachedHeaderLine = "WAVE " + (waveIndex + 1) + "/" + maxWaves;
                if (!waveState.equals("COMBAT")) {
                    String stateDisplay = waveState.equals("WAITING_NEXT_WAVE") ? "NEXT WAVE" : waveState;
                    cachedHeaderLine += " (" + stateDisplay + ")";
                }

                if (totalEnemies > 0 && waveState.equals("COMBAT")) {
                    cachedHeaderLine += "  •  Enemies: " + enemies + "/" + totalEnemies;
                }

                if (waveRemSec >= 0) {
                    int m = waveRemSec / 60;
                    int s = waveRemSec % 60;
                    cachedSubLine = "Time Limit: " + m + ":" + (s < 10 ? "0" + s : s);
                } else {
                    cachedSubLine = "Time: Unlimited";
                }
            }
        }
    }

    // ── Enemy Progress Bar ────────────────────────────────────────────────────
    private static void renderEnemyProgressBar(GuiGraphics graphics, int x, int y) {
        int enemies      = ClientStageData.getEnemiesRemaining();
        int totalEnemies = ClientStageData.getTotalEnemiesInWave();
        
        if (totalEnemies <= 0) return;

        float ratio     = (float) enemies / totalEnemies;
        float clamped   = Math.max(0.0f, Math.min(1.0f, ratio));
        int filledWidth = (int) (BAR_WIDTH * clamped);

        RenderSystem.enableBlend();

        // Frame background
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xAA111111);

        // Fill bar (filled portion left to right)
        if (filledWidth > 0) {
            int color = 0xFF55FF55; // Green
            if (clamped > 0.6f) color = 0xFFFF5555; // Red (many enemies)
            else if (clamped > 0.3f) color = 0xFFFFAA00; // Orange

            graphics.fill(x, y, x + filledWidth, y + BAR_HEIGHT, color);
        }

        RenderSystem.disableBlend();
    }

    private static int getStageColor() {
        return switch (lastStageState) {
            case "WARMUP"   -> 0xFFFFD700; // Gold
            case "SCAVENGE" -> 0xFF55FF55; // Green
            case "ENDED"    -> 0xAAAAAA; // Grey
            default         -> 0xFFFF5555; // Red
        };
    }
}
