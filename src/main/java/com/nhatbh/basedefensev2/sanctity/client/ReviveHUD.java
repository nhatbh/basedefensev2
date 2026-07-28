package com.nhatbh.basedefensev2.sanctity.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.sanctity.network.ClientReviveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ReviveHUD {

    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 8;

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(
                VanillaGuiOverlay.PLAYER_HEALTH.id(),
                "revive_hud",
                ReviveHUD::render
        );
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.renderDebug || mc.screen != null) return;

        if (ClientReviveData.isKnockedDown()) {
            renderKnockedDown(mc, graphics, width, height);
        } else {
            Integer targetId = ClientInputHandler.getCurrentTargetId();
            if (targetId != null) {
                renderRescuing(mc, graphics, width, height, targetId);
            }
        }
    }

    private static void renderRescuing(Minecraft mc, GuiGraphics graphics, int width, int height, int targetId) {
        ClientReviveData.ReviveStateData data = ClientReviveData.get(targetId);
        if (data == null || data.rescueProgress <= 0) return;

        int centerX = width / 2;
        int centerY = height / 2 + 50;

        int barX = centerX - BAR_WIDTH / 2;
        int barY = centerY + 25;

        RenderSystem.enableBlend();
        // Background
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0x99000000);

        // Fill
        float ratio = data.rescueProgress / 100.0f;
        int filledWidth = (int) (BAR_WIDTH * ratio);
        graphics.fill(barX, barY, barX + filledWidth, barY + BAR_HEIGHT, 0xFF00FF00);

        // Label
        String rescueText = "Rescuing Player...";
        int rescueWidth = mc.font.width(rescueText);
        graphics.drawString(mc.font, rescueText, centerX - rescueWidth / 2, barY - 10, 0xFF00FF00, true);

        RenderSystem.disableBlend();
    }

    private static void renderKnockedDown(Minecraft mc, GuiGraphics graphics, int width, int height) {
        int centerX = width / 2;
        int centerY = height / 2 + 50;

        // "KNOCKED DOWN" text
        String text = "KNOCKED DOWN";
        int textWidth = mc.font.width(text);
        graphics.drawString(mc.font, text, centerX - textWidth / 2, centerY, 0xFFFF0000, true);

        // Timer
        int ticks = ClientReviveData.getKnockedDownTimer();
        int seconds = (ticks / 20) % 60;
        int minutes = (ticks / 20) / 60;
        String timeText = String.format("%02d:%02d", minutes, seconds);
        int timeWidth = mc.font.width(timeText);
        graphics.drawString(mc.font, timeText, centerX - timeWidth / 2, centerY + 12, 0xFFFFFFFF, true);

        // Rescue Progress Bar
        int progress = ClientReviveData.getRescueProgress();
        if (progress > 0) {
            int barX = centerX - BAR_WIDTH / 2;
            int barY = centerY + 25;
            
            RenderSystem.enableBlend();
            // Background
            graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0x99000000);
            
            // Fill
            float ratio = progress / 100.0f;
            int filledWidth = (int) (BAR_WIDTH * ratio);
            graphics.fill(barX, barY, barX + filledWidth, barY + BAR_HEIGHT, 0xFF00FF00);
            
            // Label
            String rescueText = "Being Rescued...";
            int rescueWidth = mc.font.width(rescueText);
            graphics.drawString(mc.font, rescueText, centerX - rescueWidth / 2, barY - 10, 0xFF00FF00, true);

            RenderSystem.disableBlend();
        }

        // Give Up instruction / progress bar
        int giveUpHold = ClientInputHandler.getGiveUpHoldTicks();
        int giveUpMax = ClientInputHandler.getGiveUpRequiredTicks();

        int giveUpY = centerY + 25;
        if (progress > 0) {
            giveUpY += 35; // Offset below rescue bar if being rescued
        }

        if (giveUpHold > 0) {
            int barX = centerX - BAR_WIDTH / 2;
            RenderSystem.enableBlend();
            // Background
            graphics.fill(barX, giveUpY, barX + BAR_WIDTH, giveUpY + BAR_HEIGHT, 0x99000000);

            // Fill
            float ratio = (float) giveUpHold / giveUpMax;
            int filledWidth = (int) (BAR_WIDTH * ratio);
            graphics.fill(barX, giveUpY, barX + filledWidth, giveUpY + BAR_HEIGHT, 0xFFFF3333);

            // Label
            String giveUpText = "Giving up...";
            int giveUpWidth = mc.font.width(giveUpText);
            graphics.drawString(mc.font, giveUpText, centerX - giveUpWidth / 2, giveUpY - 10, 0xFFFF3333, true);

            RenderSystem.disableBlend();
        } else {
            String promptText = "Hold [I] to Give Up";
            int promptWidth = mc.font.width(promptText);
            graphics.drawString(mc.font, promptText, centerX - promptWidth / 2, giveUpY, 0xAAAAAA, true);
        }
    }
}
