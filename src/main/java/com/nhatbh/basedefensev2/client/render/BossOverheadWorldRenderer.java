package com.nhatbh.basedefensev2.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nhatbh.basedefensev2.boss.client.BossResourceBarRegistry;
import com.nhatbh.basedefensev2.boss.core.BossComponent;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.strength.EntityStrengthData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Boss overhead HUD rendered in world space without raycast requirement up to 40 blocks range.
 * Dynamically scales with the boss's bounding box size.
 *
 * Layout:
 *   §c❤ 450/1000                                            §f🛡 200/200   ← Text row
 *  ┌══════════════════════════════════════════════════════════════════┐ ← Strength bar (outer border)
 *  │ ██████████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │ ← Red HP bar
 *  └══════════════════════════════════════════════════════════════════┘
 *   [══════════════════════════════════════════]  [ ▮ ▮ ▮ ▮ ]            ← Shorter Passive Bar + Phase Bars (flush)
 */
@Mod.EventBusSubscriber(modid = "basedefensev2", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BossOverheadWorldRenderer {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final double MAX_RENDER_DISTANCE = 40.0D;
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    // ── Design dimensions (billboard-local units) ──
    private static final float HALF_W       = 1.0f;     // total bar width = 2.0
    private static final float STR_HEIGHT   = 0.075f;   // strength/shield component height
    private static final float INSET        = 0.010f;   // border gap on each side
    private static final float HP_HEIGHT    = STR_HEIGHT - (2 * INSET); // 0.055f (HP bar height)
    private static final float PASSIVE_H    = 0.016f;   // bottom row bar height
    private static final float PASSIVE_GAP  = 0.015f;   // gap below strength bar

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }
        if (MC.level == null || MC.player == null) {
            return;
        }

        AABB searchBox = MC.player.getBoundingBox().inflate(MAX_RENDER_DISTANCE);
        for (LivingEntity boss : MC.level.getEntitiesOfClass(LivingEntity.class, searchBox, e -> e.isAlive() && BossManager.isBoss(e))) {
            if (boss.distanceToSqr(MC.player) <= MAX_RENDER_DISTANCE_SQ) {
                renderBossOverhead(event, boss);
            }
        }
    }

    private static void renderBossOverhead(RenderLevelStageEvent event, LivingEntity boss) {
        BossComponent comp = BossManager.get(boss);
        if (comp == null || comp.getDefinition() == null) {
            return;
        }

        // ── Data ──
        float hp    = boss.getHealth();
        float maxHp = boss.getMaxHealth();
        float hpRatio = maxHp > 0 ? Mth.clamp(hp / maxHp, 0f, 1f) : 0f;

        EntityStrengthData strData = EntityStrengthData.get(boss);
        float strRatio = 1.0f;
        boolean strExhausted = false;
        if (strData != null && strData.maxStrength > 0) {
            strExhausted = strData.currentStrength <= 0 || strData.recoveryTicks > 0;
            if (strExhausted) {
                strRatio = Mth.clamp(1.0f - (strData.recoveryTicks / 300.0f), 0f, 1f);
            } else {
                strRatio = Mth.clamp(strData.currentStrength / strData.maxStrength, 0f, 1f);
            }
        }

        int phaseIdx    = comp.getCurrentPhaseIndex();
        int totalPhases = comp.getDefinition().getPhases().size();

        // Calculate size scale factor relative to standard mob width (0.6f)
        float sizeScale = Mth.clamp(boss.getBbWidth() / 0.6f, 0.75f, 4.0f);

        // ── Billboard transform ──
        Camera camera = MC.gameRenderer.getMainCamera();
        Vec3 camPos   = camera.getPosition();
        PoseStack pose = event.getPoseStack();

        pose.pushPose();

        double lx = Mth.lerp(event.getPartialTick(), boss.xo, boss.getX()) - camPos.x;
        double ly = Mth.lerp(event.getPartialTick(), boss.yo, boss.getY()) - camPos.y;
        double lz = Mth.lerp(event.getPartialTick(), boss.zo, boss.getZ()) - camPos.z;

        pose.translate(lx, ly + boss.getBbHeight() + (0.45f * sizeScale), lz);
        pose.mulPose(camera.rotation());
        pose.scale(-sizeScale, -sizeScale, sizeScale); // flip so +Y = screen up & scale with boss size

        Matrix4f mat = pose.last().pose();

        // ════════════════════════════════════════════════════════
        // Pass 1: Geometry
        // ════════════════════════════════════════════════════════
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        float left  = -HALF_W;
        float right =  HALF_W;
        float barW  = right - left;

        // Bar center Y = 0. Strength bar spans [−STR_HEIGHT/2 .. +STR_HEIGHT/2]
        float strTop = STR_HEIGHT / 2f;
        float strBot = -STR_HEIGHT / 2f;
        float textY  = strTop + 0.02f; // base Y of top text row

        // ── Dark background behind main bar ──
        quad(mat, buf, left - 0.012f, strTop + 0.012f, right + 0.012f, strBot - 0.012f,
                0.04f, 0.04f, 0.04f, 0.85f);

        // ── Strength bar fill (acts as border for HP bar) ──
        // Color: gold when healthy, purple when exhausted, gray when recovering
        float sR, sG, sB;
        if (strExhausted) { sR = 0.65f; sG = 0.15f; sB = 0.80f; } // purple recovery
        else              { sR = 1.0f;  sG = 0.78f; sB = 0.1f;  } // gold

        float strFillRight = left + barW * strRatio;
        quad(mat, buf, left, strTop, strFillRight, strBot, sR, sG, sB, 0.9f);

        // ── HP bar (inset inside strength bar by INSET on each side) ──
        float hpLeft  = left  + INSET;
        float hpRight = right - INSET;
        float hpTop   = strTop - INSET;
        float hpBot   = strBot + INSET;
        float hpW     = hpRight - hpLeft;

        // HP dark background (inner)
        quad(mat, buf, hpLeft, hpTop, hpRight, hpBot,
                0.1f, 0.1f, 0.1f, 0.9f);

        // HP red fill
        if (hpRatio > 0) {
            float hpFillRight = hpLeft + hpW * hpRatio;
            quadGradient(mat, buf, hpLeft, hpTop, hpFillRight, hpBot,
                    0.88f, 0.14f, 0.14f, 0.95f,
                    0.58f, 0.06f, 0.06f, 0.95f);
        }

        // ════════════════════════════════════════════════════════
        // Bottom Row: Passive Resource Bar (left) + Phase Bars (right)
        // Spans [left .. right] - perfectly flush with main HP/strength bar above.
        // ════════════════════════════════════════════════════════
        float pTop = strBot - PASSIVE_GAP;
        float pBot = pTop - PASSIVE_H;

        BossResourceBarRegistry.ResourceBarInfo barInfo = BossResourceBarRegistry.getBar(boss);
        boolean hasPassive = barInfo != null && barInfo.maxSupplier.get() > 0;
        boolean hasPhases  = totalPhases > 1;

        if (hasPassive || hasPhases) {
            float rowGap = (hasPassive && hasPhases) ? 0.02f : 0f;
            float pSegGap = 0.008f; // small horizontal gap between phase bar segments

            // Calculate total width reserved for phase bars on the right
            float phaseGroupW = 0f;
            float pSegW = 0f;
            if (hasPhases) {
                phaseGroupW = hasPassive ? Math.min(0.50f, barW * 0.25f) : barW;
                pSegW = (phaseGroupW - (totalPhases - 1) * pSegGap) / totalPhases;
            }

            // Passive bar width fills the remaining left portion
            float passiveW = hasPassive ? (barW - phaseGroupW - rowGap) : 0f;

            // ── 1. Render Passive Bar (on the left) ──
            if (hasPassive) {
                float passLeft  = left;
                float passRight = left + passiveW;
                float val       = barInfo.currentSupplier.get();
                float maxVal    = barInfo.maxSupplier.get();
                float passiveRatio = Mth.clamp(val / maxVal, 0f, 1f);

                // Passive BG frame
                quad(mat, buf, passLeft - 0.003f, pTop + 0.003f, passRight + 0.003f, pBot - 0.003f,
                        0.04f, 0.04f, 0.04f, 0.8f);

                // Passive fill
                int topCol = barInfo.colorTopSupplier.get();
                float pR = ((topCol >> 16) & 0xFF) / 255.0f;
                float pG = ((topCol >> 8)  & 0xFF) / 255.0f;
                float pB = (topCol         & 0xFF) / 255.0f;
                if (passiveRatio > 0) {
                    quad(mat, buf, passLeft, pTop, passLeft + passiveW * passiveRatio, pBot, pR, pG, pB, 0.9f);
                }
            }

            // ── 2. Render Phase Bars (on the right, flush with right edge) ──
            if (hasPhases) {
                float phaseGroupLeft = right - phaseGroupW;
                int remainingPhases = totalPhases - phaseIdx;

                for (int i = 0; i < totalPhases; i++) {
                    float bLeft  = phaseGroupLeft + i * (pSegW + pSegGap);
                    float bRight = bLeft + pSegW;

                    // Dark frame
                    quad(mat, buf, bLeft - 0.002f, pTop + 0.002f, bRight + 0.002f, pBot - 0.002f,
                            0.04f, 0.04f, 0.04f, 0.9f);

                    if (i < remainingPhases) {
                        // Active Phase: Gold Gradient (bright gold to dark gold)
                        quadGradient(mat, buf, bLeft, pTop, bRight, pBot,
                                1.0f, 0.84f, 0.0f, 1.0f,
                                0.70f, 0.45f, 0.0f, 1.0f);
                    } else {
                        // Defeated Phase: Dark Gray
                        quad(mat, buf, bLeft, pTop, bRight, pBot,
                                0.20f, 0.20f, 0.20f, 0.85f);
                    }
                }
            }
        }

        tess.end();

        // ════════════════════════════════════════════════════════
        // Pass 2: Text labels (HP left, Shield right)
        // ════════════════════════════════════════════════════════
        Font font = MC.font;
        MultiBufferSource.BufferSource textBuf = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

        float textScale = 0.012f;

        int light = MC.getEntityRenderDispatcher().getPackedLightCoords(boss, event.getPartialTick());

        // HP text (left-aligned): §c❤ 450/1000
        String hpText = "§c❤ " + (int) hp + "/" + (int) maxHp;
        pose.pushPose();
        pose.translate(left, textY, 0);
        pose.scale(textScale, textScale, textScale);
        Matrix4f textMat = pose.last().pose();
        font.drawInBatch(hpText, 0, 0, 0xFFFFFFFF, false, textMat, textBuf,
                Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();

        // Shield text (right-aligned): §f🛡 200/200 or §e🛡 12.5s
        String shieldText;
        if (strData != null && strData.maxStrength > 0) {
            if (strExhausted) {
                float secs = strData.recoveryTicks / 20.0f;
                shieldText = "§e🛡 " + String.format("%.1fs", secs);
            } else {
                shieldText = "§f🛡 " + (int) strData.currentStrength + "/" + (int) strData.maxStrength;
            }
        } else {
            shieldText = "";
        }

        if (!shieldText.isEmpty()) {
            float shieldWidth = font.width(shieldText) * textScale;
            pose.pushPose();
            pose.translate(right - shieldWidth, textY, 0);
            pose.scale(textScale, textScale, textScale);
            Matrix4f shieldMat = pose.last().pose();
            font.drawInBatch(shieldText, 0, 0, 0xFFFFFFFF, false, shieldMat, textBuf,
                    Font.DisplayMode.NORMAL, 0, light);
            pose.popPose();
        }

        textBuf.endBatch();

        // ── Restore ──
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        pose.popPose();
    }

    // ═══════════════════════════════════════════════════════════
    // Geometry helpers
    // ═══════════════════════════════════════════════════════════

    private static void quad(Matrix4f m, BufferBuilder b,
                             float x1, float y1, float x2, float y2,
                             float r, float g, float bl, float a) {
        b.vertex(m, x1, y1, 0).color(r, g, bl, a).endVertex();
        b.vertex(m, x1, y2, 0).color(r, g, bl, a).endVertex();
        b.vertex(m, x2, y2, 0).color(r, g, bl, a).endVertex();
        b.vertex(m, x1, y1, 0).color(r, g, bl, a).endVertex();
        b.vertex(m, x2, y2, 0).color(r, g, bl, a).endVertex();
        b.vertex(m, x2, y1, 0).color(r, g, bl, a).endVertex();
    }

    private static void quadGradient(Matrix4f m, BufferBuilder b,
                                     float x1, float y1, float x2, float y2,
                                     float r1, float g1, float b1, float a1,
                                     float r2, float g2, float b2, float a2) {
        b.vertex(m, x1, y1, 0).color(r1, g1, b1, a1).endVertex();
        b.vertex(m, x1, y2, 0).color(r2, g2, b2, a2).endVertex();
        b.vertex(m, x2, y2, 0).color(r2, g2, b2, a2).endVertex();
        b.vertex(m, x1, y1, 0).color(r1, g1, b1, a1).endVertex();
        b.vertex(m, x2, y2, 0).color(r2, g2, b2, a2).endVertex();
        b.vertex(m, x2, y1, 0).color(r1, g1, b1, a1).endVertex();
    }
}
