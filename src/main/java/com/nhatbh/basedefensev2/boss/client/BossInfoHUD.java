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

    private static final int HP_BAR_WIDTH   = 220;
    private static final int HP_BAR_HEIGHT  = 4;  // Reduced height 
    private static final int STR_BAR_WIDTH  = 170; // Slightly shorter for a tiered look
    private static final int STR_BAR_HEIGHT = 2;  // Reduced height
    private static final int MARGIN_TOP     = 10;
    
    private static final int ORB_SIZE       = 4;  // Sized down from 8
    private static final int ORB_SPACING    = 2;

    // ── Colors ───────────────────────────────────────────────────────────────
    private static final int COLOR_GOLD_BRIGHT = 0xFFFFD700;
    private static final int COLOR_GOLD_DARK   = 0xFFAA7700;
    private static final int COLOR_FRAME_BG    = 0xFF1A1A1A;
    private static final int COLOR_SHADOW      = 0xFF000000;

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
        if (mc.options.renderDebug || mc.screen != null || mc.level == null || mc.player == null) return;

        Player player = mc.player;
        List<LivingEntity> nearbyBosses = mc.level.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(64)
        ).stream().filter(BossManager::isBoss).toList();

        if (nearbyBosses.isEmpty()) return;

        LivingEntity boss = nearbyBosses.stream()
                .min(Comparator.<LivingEntity>comparingDouble(b -> b.distanceToSqr(player)))
                .orElse(null);

        if (boss == null || !boss.isAlive()) return;

        BossComponent comp = BossManager.get(boss);
        if (comp == null) return;

        String bossName = boss.getDisplayName().getString();
        int bossHp = (int) Math.ceil(boss.getHealth());
        int bossMaxHp = (int) boss.getMaxHealth();
        
        EntityStrengthData strengthData = EntityStrengthData.get(boss);
        int bossStrength = strengthData != null ? (int) strengthData.currentStrength : 0;
        int bossMaxStrength = strengthData != null ? (int) strengthData.maxStrength : 1;

        int currentPhase = comp.getCurrentPhaseIndex() + 1;
        int maxPhases = comp.getDefinition().getPhases().size();

        int centerX = width / 2;
        int currentY = MARGIN_TOP;

        graphics.pose().pushPose();
        
        // --- Scale down text by 50% ---
        graphics.pose().scale(0.5f, 0.5f, 1.0f);
        
        // Note: Because we scaled down by 0.5, our rendering coordinates for text 
        // need to be multiplied by 2 to stay at the correct screen position
        float textScaleY = (currentY) * 2;
        
        // 1. Render Boss Name with Ornate Flourishes
        int nameWidth = mc.font.width(bossName);
        float textScaleCenterX = centerX * 2;
        
        graphics.pose().popPose(); // Pop scale for flourishes
        renderTextFlourish(graphics, centerX, currentY, (int)(nameWidth * 0.5f));
        
        graphics.pose().pushPose();
        graphics.pose().scale(0.5f, 0.5f, 1.0f);
        graphics.drawString(mc.font, bossName, (int)(textScaleCenterX - (nameWidth / 2)), (int)textScaleY, 0xFF5555, true);
        graphics.pose().popPose(); // Pop scale after text

        currentY += 8;

        // 2. Render Jewel Phase Orbs
        renderJewelOrbs(graphics, centerX, currentY, currentPhase, maxPhases);
        if (maxPhases > 1) {
            currentY += ORB_SIZE + 4;
        }

        // 3. Render Ornate HP Bar
        int hpStartX = centerX - (HP_BAR_WIDTH / 2);
        renderOrnateBar(graphics, hpStartX, currentY, HP_BAR_WIDTH, HP_BAR_HEIGHT, bossHp, bossMaxHp, 0xFFFF4444, 0xFF880000);
        
        // HP Text Centered
        String hpText = bossHp + " / " + bossMaxHp;
        int hpTextWidth = mc.font.width(hpText);
        
        graphics.pose().pushPose();
        graphics.pose().scale(0.5f, 0.5f, 1.0f);
        graphics.drawString(mc.font, hpText, (int)(centerX * 2 - (hpTextWidth / 2)), (int)((currentY + 1) * 2), 0xFFFFFF, true);
        graphics.pose().popPose(); // Pop after text
        
        currentY += HP_BAR_HEIGHT + 3; // Add gap for the frame

        // 4. Render Ornate Strength Bar (Tiered below HP)
        if (bossMaxStrength > 0 && strengthData != null) {
            int strStartX = centerX - (STR_BAR_WIDTH / 2);
            renderOrnateBar(graphics, strStartX, currentY, STR_BAR_WIDTH, STR_BAR_HEIGHT, bossStrength, bossMaxStrength, 0xFFFFAA00, 0xFF884400);
        }
    }

    private static void renderTextFlourish(GuiGraphics graphics, int centerX, int y, int textWidth) {
        int lineLength = 20; // smaller flourish
        int leftEnd = centerX - (textWidth / 2) - 4;
        int rightStart = centerX + (textWidth / 2) + 4;
        int lineY = y + 2; // Vertically center 

        // Left Line
        graphics.fillGradient(leftEnd - lineLength, lineY, leftEnd, lineY + 1, COLOR_SHADOW, COLOR_GOLD_BRIGHT);
        graphics.fill(leftEnd - 1, lineY - 1, leftEnd + 1, lineY + 2, COLOR_GOLD_BRIGHT); // Stud

        // Right Line
        graphics.fillGradient(rightStart, lineY, rightStart + lineLength, lineY + 1, COLOR_GOLD_BRIGHT, COLOR_SHADOW);
        graphics.fill(rightStart - 1, lineY - 1, rightStart + 1, lineY + 2, COLOR_GOLD_BRIGHT); // Stud
    }

    private static void renderJewelOrbs(GuiGraphics graphics, int centerX, int y, int current, int max) {
        if (max <= 1) return;

        int totalWidth = (max * ORB_SIZE) + ((max - 1) * ORB_SPACING);
        int startX = centerX - (totalWidth / 2);
        int remainingPhases = max - current + 1;

        for (int i = 0; i < max; i++) {
            int orbX = startX + (i * (ORB_SIZE + ORB_SPACING));

            if (i < remainingPhases) {
                // Active Gem (Gradient Red)
                graphics.fillGradient(orbX, y, orbX + ORB_SIZE, y + ORB_SIZE, 0xFFFF3333, 0xFF660000);
                // Gem Highlight (Top Left glint)
                graphics.fill(orbX, y, orbX + 2, y + 2, 0xFFFFCCCC);
            } else {
                // Empty Socket (Dark indented look)
                graphics.fillGradient(orbX, y, orbX + ORB_SIZE, y + ORB_SIZE, 0xFF222222, 0xFF050505);
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
