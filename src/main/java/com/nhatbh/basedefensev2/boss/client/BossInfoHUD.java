package com.nhatbh.basedefensev2.boss.client;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.boss.core.BossComponent;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.strength.EntityStrengthData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BossInfoHUD {

    private static final int HP_BAR_WIDTH   = 140;
    private static final int HP_BAR_HEIGHT  = 7;
    private static final int STR_BAR_WIDTH  = 120;
    private static final int STR_BAR_HEIGHT = 5;
    private static final int RES_BAR_WIDTH  = 120;
    private static final int RES_BAR_HEIGHT = 5;

    private static final int MARGIN_LEFT    = 10;
    private static final int MARGIN_TOP     = 28;

    private static final int COLOR_GOLD_BRIGHT = 0xFFFFD700;
    private static final int COLOR_GOLD_DARK   = 0xFFAA7700;
    private static final int COLOR_FRAME_BG    = 0xFF1A1A1A;

    private static int lastRenderedBottomY  = MARGIN_TOP;

    public static int getBottomY() {
        return lastRenderedBottomY;
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(
                VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(),
                "rpg_boss_hud",
                BossInfoHUD::render
        );
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.renderDebug || mc.screen != null || mc.level == null || mc.player == null) {
            lastRenderedBottomY = MARGIN_TOP;
            return;
        }

        Player player = mc.player;
        List<LivingEntity> nearbyBosses = mc.level.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(64)
        ).stream().filter(BossManager::isBoss).toList();

        if (nearbyBosses.isEmpty()) {
            lastRenderedBottomY = MARGIN_TOP;
            return;
        }

        LivingEntity boss = nearbyBosses.stream()
                .min(Comparator.<LivingEntity>comparingDouble(b -> b.distanceToSqr(player)))
                .orElse(null);

        if (boss == null || !boss.isAlive()) {
            lastRenderedBottomY = MARGIN_TOP;
            return;
        }

        BossComponent comp = BossManager.get(boss);
        if (comp == null) {
            lastRenderedBottomY = MARGIN_TOP;
            return;
        }

        int bossHp = (int) Math.ceil(boss.getHealth());
        int bossMaxHp = (int) boss.getMaxHealth();

        EntityStrengthData strengthData = EntityStrengthData.get(boss);
        int bossStrength = strengthData != null ? (int) strengthData.currentStrength : 0;
        int bossMaxStrength = strengthData != null ? (int) strengthData.maxStrength : 1;

        int currentPhase = comp.getCurrentPhaseIndex() + 1;
        int maxPhases = comp.getDefinition().getPhases().size();

        int startX = MARGIN_LEFT;
        int currentY = MARGIN_TOP;

        // 1. Render Yellow Phase Bars (Height: 2px)
        if (maxPhases > 1) {
            renderPhaseBars(graphics, startX, currentY, currentPhase, maxPhases);
            currentY += 5;
        }

        // 2. Render HP Bar
        renderOrnateBar(graphics, startX, currentY, HP_BAR_WIDTH, HP_BAR_HEIGHT, bossHp, bossMaxHp, 0xFFFF4444, 0xFF880000);
        currentY += HP_BAR_HEIGHT + 2;

        // Larger HP Text (Scale 0.65f)
        String hpText = bossHp + " / " + bossMaxHp;
        graphics.pose().pushPose();
        graphics.pose().scale(0.65f, 0.65f, 1.0f);
        graphics.drawString(mc.font, hpText, (int) (startX / 0.65f), (int) (currentY / 0.65f), 0xFFFFFFFF, true);
        graphics.pose().popPose();

        currentY += (int) (mc.font.lineHeight * 0.65f) + 2;

        // 3. Render Strength Bar (with recovery state support)
        if (bossMaxStrength > 0 && strengthData != null) {
            boolean isRecovering = strengthData.currentStrength <= 0 || strengthData.recoveryTicks > 0;
            if (isRecovering) {
                int fillVal = strengthData.recoveryTicks > 0 ? strengthData.recoveryTicks : 300;
                int maxVal = 300;
                renderOrnateBar(graphics, startX, currentY, STR_BAR_WIDTH, STR_BAR_HEIGHT, fillVal, maxVal, 0xFF9933FF, 0xFF4B0082);
                
                String recText = String.format("Exhausted (%.1fs)", strengthData.recoveryTicks / 20.0f);
                graphics.pose().pushPose();
                graphics.pose().scale(0.55f, 0.55f, 1.0f);
                graphics.drawString(mc.font, recText, (int) ((startX + STR_BAR_WIDTH + 4) / 0.55f), (int) (currentY / 0.55f), 0xFFD8BFD8, true);
                graphics.pose().popPose();
            } else {
                renderOrnateBar(graphics, startX, currentY, STR_BAR_WIDTH, STR_BAR_HEIGHT, bossStrength, bossMaxStrength, 0xFFFFAA00, 0xFF884400);
            }
            currentY += STR_BAR_HEIGHT + 4;
        }

        // 4. Render Passive Secondary Resource Bar
        BossResourceBarRegistry.ResourceBarInfo barInfo = BossResourceBarRegistry.getBar(boss);
        if (barInfo != null) {
            float currentVal = barInfo.currentSupplier.get();
            float maxVal = barInfo.maxSupplier.get();
            if (maxVal > 0) {
                String labelText = barInfo.nameSupplier.get() + ": " + (int) currentVal + " / " + (int) maxVal;
                graphics.pose().pushPose();
                graphics.pose().scale(0.6f, 0.6f, 1.0f);
                graphics.drawString(mc.font, labelText, (int) (startX / 0.6f), (int) (currentY / 0.6f), 0xFFFFFFFF, true);
                graphics.pose().popPose();

                currentY += (int) (mc.font.lineHeight * 0.6f) + 2;

                renderOrnateBar(graphics, startX, currentY, RES_BAR_WIDTH, RES_BAR_HEIGHT, (int) currentVal, (int) maxVal, barInfo.colorTopSupplier.get(), barInfo.colorBottomSupplier.get());
                currentY += RES_BAR_HEIGHT + 2;
            }
        }

        lastRenderedBottomY = currentY;
    }

    private static void renderPhaseBars(GuiGraphics graphics, int startX, int y, int current, int max) {
        if (max <= 1) return;

        int barWidth = 12;
        int barHeight = 2;
        int spacing = 2;
        int remainingPhases = max - current + 1;

        for (int i = 0; i < max; i++) {
            int barX = startX + (i * (barWidth + spacing));

            if (i < remainingPhases) {
                // Active Phase (Bright Yellow / Gold Gradient)
                graphics.fillGradient(barX, y, barX + barWidth, y + barHeight, COLOR_GOLD_BRIGHT, COLOR_GOLD_DARK);
            } else {
                // Defeated Phase (Dark Gray)
                graphics.fill(barX, y, barX + barWidth, y + barHeight, 0xFF333333);
            }
        }
    }

    private static void renderOrnateBar(GuiGraphics graphics, int x, int y, int width, int height, int current, int max, int colorTop, int colorBottom) {
        if (max <= 0) return;

        // Background
        graphics.fill(x, y, x + width, y + height, COLOR_FRAME_BG);

        float ratio = (float) current / max;
        float clamped = Math.max(0.0f, Math.min(1.0f, ratio));
        int filledWidth = (int) (width * clamped);

        if (filledWidth > 0) {
            graphics.fillGradient(x, y, x + filledWidth, y + height, colorTop, colorBottom);
            graphics.fill(x, y, x + filledWidth, y + 1, 0x55FFFFFF);
        }
    }
}
