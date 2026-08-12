package com.nhatbh.basedefensev2.sanctity.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.sanctity.network.ClientSanctityData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Renders the Altar's Sanctity (Hearts / Lives) and Grace (Energy Bar) at the bottom-left of the screen.
 * Data is supplied by {@link ClientSanctityData}, which is updated by
 * {@link com.nhatbh.basedefensev2.sanctity.network.SanctitySyncPacket}.
 */
@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AltarPowerHUD {

    private static final int BAR_WIDTH    = 80;
    private static final int BAR_HEIGHT   = 6;
    private static final int MARGIN_LEFT   = 10;
    private static final int MARGIN_BOTTOM = 10;

    // ── Text cache ───────────────────────────────────────────────────────────

    private static String cachedGraceText = "";
    private static int cachedGraceTextWidth = 0;

    // ── State trackers ───────────────────────────────────────────────────────

    private static int lastSanctity    = -1;
    private static int lastMaxSanctity = -1;
    private static double lastGrace    = -1.0;
    private static int lastMaxGrace    = -1;

    // ── Registration ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(
                VanillaGuiOverlay.PLAYER_HEALTH.id(), // Render above standard health/food
                "altar_power_hud",
                AltarPowerHUD::render
        );
    }

    // ── Render entry point ───────────────────────────────────────────────────

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.renderDebug || mc.screen != null) return;

        updateCaches(mc);

        // Calculate positions for bottom-left screen alignment
        int startX = MARGIN_LEFT;
        int graceBarY = height - MARGIN_BOTTOM - BAR_HEIGHT;
        int heartsY = graceBarY - 14;

        // Render Hearts for Sanctity Lives
        renderSanctityHearts(graphics, mc, startX, heartsY, lastSanctity, lastMaxSanctity);

        // Render Grace Bar (Blue)
        renderHorizontalBar(graphics, mc, startX, graceBarY, (float) lastGrace, (float) lastMaxGrace, 0xFF3333FF, cachedGraceText, cachedGraceTextWidth);
    }

    // ── Cache update ─────────────────────────────────────────────────────────

    private static void updateCaches(Minecraft mc) {
        int sanctity    = ClientSanctityData.getSanctity();
        int maxSanctity = ClientSanctityData.getMaxSanctity();
        double grace    = ClientSanctityData.getGrace();
        int maxGrace    = ClientSanctityData.getMaxGrace();

        lastSanctity    = sanctity;
        lastMaxSanctity = maxSanctity;

        if (Math.abs(grace - lastGrace) > 0.01 || maxGrace != lastMaxGrace) {
            lastGrace    = grace;
            lastMaxGrace = maxGrace;
            cachedGraceText = (int) grace + "/" + maxGrace;
            cachedGraceTextWidth = mc.font.width(cachedGraceText);
        }
    }

    // ── Hearts Rendering ─────────────────────────────────────────────────────

    /**
     * Renders Sanctity as a row of Heart icons with current/max life counter.
     */
    private static void renderSanctityHearts(GuiGraphics graphics, Minecraft mc, int x, int y, int currentLives, int maxLives) {
        if (maxLives <= 0) return;

        RenderSystem.enableBlend();

        // Label header (e.g. "SANCTITY 10/10")
        String label = "§f§lSANCTITY §c" + currentLives + "§7/" + maxLives;
        graphics.drawString(mc.font, label, x, y - 9, 0xFFFFFF, true);

        // Build heart row string (e.g. §c❤ §c❤ §c❤ §8❤ §8❤)
        StringBuilder heartsStr = new StringBuilder();
        for (int i = 0; i < maxLives; i++) {
            if (i < currentLives) {
                heartsStr.append("§c❤ ");
            } else {
                heartsStr.append("§8❤ ");
            }
        }

        graphics.drawString(mc.font, heartsStr.toString().trim(), x, y + 1, 0xFFFFFF, true);

        RenderSystem.disableBlend();
    }

    // ── Horizontal Progress Bar (For Grace) ──────────────────────────────────

    private static void renderHorizontalBar(GuiGraphics graphics, Minecraft mc, int x, int y, float current, float max, int color, String text, int textWidth) {
        if (max <= 0) return;

        float ratio = current / max;
        float clamped = Math.max(0.0f, Math.min(1.0f, ratio));
        int filledWidth = (int) (BAR_WIDTH * clamped);

        RenderSystem.enableBlend();

        // Background
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0x99000000);

        // Fill
        if (filledWidth > 0) {
            graphics.fill(x, y, x + filledWidth, y + BAR_HEIGHT, color);
        }

        // Text
        graphics.pose().pushPose();
        graphics.pose().translate(x + BAR_WIDTH / 2f, y + BAR_HEIGHT / 2f, 0);
        graphics.pose().scale(0.45f, 0.45f, 0.45f);
        graphics.drawString(mc.font, text, (int) (-textWidth / 2f), -4, 0xFFFFFF, true);
        graphics.pose().popPose();

        // Border
        graphics.fill(x,               y,                  x + BAR_WIDTH, y + 1,              0x44FFFFFF);
        graphics.fill(x,               y + BAR_HEIGHT - 1, x + BAR_WIDTH, y + BAR_HEIGHT,     0x44FFFFFF);
        graphics.fill(x,               y,                  x + 1,         y + BAR_HEIGHT,     0x44FFFFFF);
        graphics.fill(x + BAR_WIDTH-1, y,                  x + BAR_WIDTH, y + BAR_HEIGHT,     0x44FFFFFF);

        RenderSystem.disableBlend();
    }
}
