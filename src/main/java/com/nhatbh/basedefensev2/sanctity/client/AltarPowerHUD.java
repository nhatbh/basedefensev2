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
 * Renders the Altar's Sanctity and Grace at the right-middle of the screen.
 * Data is supplied by {@link ClientSanctityData}, which is updated by
 * {@link com.nhatbh.basedefensev2.sanctity.network.SanctitySyncPacket}.
 */
@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AltarPowerHUD {

    private static final int BAR_WIDTH    = 80;
    private static final int BAR_HEIGHT   = 6;
    private static final int MARGIN_LEFT   = 10;
    private static final int MARGIN_BOTTOM = 10;
    private static final int BAR_SPACING  = 2; // Space between the two bars

    // ── Text cache ───────────────────────────────────────────────────────────

    private static String cachedSanctityText = "";
    private static String cachedGraceText    = "";

    private static int cachedSanctityTextWidth = 0;
    private static int cachedGraceTextWidth    = 0;

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
        int sanctityBarY = graceBarY - BAR_HEIGHT - BAR_SPACING;

        // Render Bars
        renderHorizontalBar(graphics, mc, startX, sanctityBarY, lastSanctity, lastMaxSanctity, 0xFFFF3333, cachedSanctityText, cachedSanctityTextWidth); // Red
        renderHorizontalBar(graphics, mc, startX, graceBarY, (float)lastGrace, (float)lastMaxGrace, 0xFF3333FF, cachedGraceText, cachedGraceTextWidth);       // Blue
    }

    // ── Cache update ─────────────────────────────────────────────────────────

    private static void updateCaches(Minecraft mc) {
        int sanctity    = ClientSanctityData.getSanctity();
        int maxSanctity = ClientSanctityData.getMaxSanctity();
        double grace    = ClientSanctityData.getGrace();
        int maxGrace    = ClientSanctityData.getMaxGrace();

        if (sanctity != lastSanctity || maxSanctity != lastMaxSanctity) {
            lastSanctity    = sanctity;
            lastMaxSanctity = maxSanctity;
            cachedSanctityText = sanctity + "/" + maxSanctity;
            cachedSanctityTextWidth = mc.font.width(cachedSanctityText);
        }

        if (Math.abs(grace - lastGrace) > 0.01 || maxGrace != lastMaxGrace) {
            lastGrace    = grace;
            lastMaxGrace = maxGrace;
            cachedGraceText = (int)grace + "/" + maxGrace;
            cachedGraceTextWidth = mc.font.width(cachedGraceText);
        }
    }

    // ── Vertical Progress Bar ────────────────────────────────────────────────

    /**
     * Renders a horizontal bar that fills from LEFT to RIGHT.
     */
    private static void renderHorizontalBar(GuiGraphics graphics, Minecraft mc, int x, int y, float current, float max, int color, String text, int textWidth) {
        if (max <= 0) return;

        float ratio = current / max;
        float clamped = Math.max(0.0f, Math.min(1.0f, ratio));
        int filledWidth = (int) (BAR_WIDTH * clamped);

        RenderSystem.enableBlend();

        // Background (semi-transparent black)
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0x99000000);

        // Fill - Anchored to the LEFT
        if (filledWidth > 0) {
            graphics.fill(x, y, x + filledWidth, y + BAR_HEIGHT, color);
        }

        // Draw text inside (centered horizontally and vertically, scaled down)
        graphics.pose().pushPose();
        graphics.pose().translate(x + BAR_WIDTH / 2f, y + BAR_HEIGHT / 2f, 0);
        graphics.pose().scale(0.45f, 0.45f, 0.45f);
        graphics.drawString(mc.font, text, (int)(-textWidth / 2f), -4, 0xFFFFFF, true);
        graphics.pose().popPose();

        // Border (subtle grey/white outline)
        graphics.fill(x,               y,                  x + BAR_WIDTH, y + 1,              0x44FFFFFF); // Top
        graphics.fill(x,               y + BAR_HEIGHT - 1, x + BAR_WIDTH, y + BAR_HEIGHT,     0x44FFFFFF); // Bottom
        graphics.fill(x,               y,                  x + 1,         y + BAR_HEIGHT,     0x44FFFFFF); // Left
        graphics.fill(x + BAR_WIDTH-1, y,                  x + BAR_WIDTH, y + BAR_HEIGHT,     0x44FFFFFF); // Right

        RenderSystem.disableBlend();
    }
}
