package com.nhatbh.basedefensev2.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.boss.client.ClientSkillHandler;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.skills.BossSkillData;
import com.nhatbh.basedefensev2.boss.skills.SkillIndicatorData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobSkillRenderer {

    private static final Minecraft MC = Minecraft.getInstance();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;
        if (MC.level == null) return;

        PoseStack pose = event.getPoseStack();
        Camera cam = event.getCamera();
        Vec3 camPos = cam.getPosition();

        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (SkillIndicatorData data : ClientSkillHandler.getAllActiveSkills()) {
            Entity entity = MC.level.getEntity(data.entityId);
            if (entity instanceof LivingEntity living && entity.isAlive()) {
                BossSkillData nbtData = BossSkillData.get(living);
                if (nbtData != null) {
                    renderSkillIndicator(pose, buffer, living, nbtData);
                }
            }
        }

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void renderSkillIndicator(PoseStack pose, BufferBuilder buffer, LivingEntity entity, BossSkillData data) {
        float partialTicks = MC.getFrameTime();
        double x = entity.xOld + (entity.getX() - entity.xOld) * partialTicks;
        double y = entity.yOld + (entity.getY() - entity.yOld) * partialTicks + 0.1; // Slightly higher than strength circle
        double z = entity.zOld + (entity.getZ() - entity.zOld) * partialTicks;

        float radiusInner = entity.getBbWidth() * 0.9f;
        float radiusOuter = entity.getBbWidth() * 1.1f;
        if (radiusInner < 0.6f) radiusInner = 0.6f;
        if (radiusOuter < 0.8f) radiusOuter = 0.8f;

        int totalSegments = 64;
        float step = (float) (2 * Math.PI / totalSegments);
        PoseStack.Pose p = pose.last();

        float cx = (float) x;
        float cy = (float) y;
        float cz = (float) z;

        float currentTick = data.tickInStep + partialTicks;
        float progressPercent = Math.min(1.0f, currentTick / data.totalDuration);

        for (int i = 0; i < totalSegments; i++) {
            float startAngle = i * step;
            float endAngle = (i + 1) * step;
            float segmentMidAngle = (startAngle + endAngle) / 2.0f;

            float r = 0.5f, g = 0.5f, b = 0.5f, a = 0.4f; // Default: Gray

            // User specifies only 3 types of counter rendering.
            if (data.counterType == ActiveSequence.CounterType.NORMAL) {
                a = 0.8f;
                if (currentTick < data.counterWindowStart) {
                    r = 1.0f; g = 0.0f; b = 0.0f; // Red ring
                } else if (currentTick <= data.counterWindowEnd) {
                    r = 0.0f; g = 1.0f; b = 0.0f; // Green ring
                } else {
                    continue; // No render after window
                }
            } else if (data.counterType == ActiveSequence.CounterType.DIRECTIONAL) {
                // Green arc: counter direction only
                if (data.counterDirection != null) {
                    Vec3 segmentDir = new Vec3(Math.cos(segmentMidAngle), 0, Math.sin(segmentMidAngle));
                    double dot = segmentDir.dot(data.counterDirection);
                    if (dot > 0.5) { // Roughly 90 degree arc
                        a = 0.8f;
                        r = 0.0f; g = 1.0f; b = 0.0f; // Green
                    } else {
                        continue; // No render for wrong direction
                    }
                } else {
                    continue;
                }
            } else if (data.counterType == ActiveSequence.CounterType.MAGIC) {
                // Flashing ring: magic counter
                float time = (float) (System.currentTimeMillis() % 1000L) / 1000.0f;
                float flash = (float) Math.sin(time * 2.0f * Math.PI) * 0.5f + 0.5f;
                a = flash * 0.8f;
                if (data.magicElement != null) {
                    float[] rgb = getElementColor(data.magicElement);
                    r = rgb[0]; g = rgb[1]; b = rgb[2];
                } else {
                    r = 1.0f; g = 1.0f; b = 1.0f;
                }
            } else {
                continue; // Other types don't need rendering
            }

            renderArcSegment(p, buffer, cx, cy, cz, startAngle, endAngle, radiusInner, radiusOuter, r, g, b, a);
        }
    }

    private static float[] getElementColor(com.nhatbh.basedefensev2.elemental.ElementType element) {
        return switch (element) {
            case FIRE -> new float[]{1.0f, 0.3f, 0.0f};
            case ICE -> new float[]{0.3f, 0.7f, 1.0f};
            case LIGHTNING -> new float[]{0.8f, 0.4f, 1.0f};
            case AQUA -> new float[]{0.1f, 0.3f, 1.0f};
            case NATURE -> new float[]{0.1f, 1.0f, 0.2f};
            case HOLY -> new float[]{1.0f, 1.0f, 0.6f};
            case ENDER -> new float[]{0.5f, 0.0f, 1.0f};
            case BLOOD -> new float[]{0.7f, 0.0f, 0.0f};
            case EVOCATION -> new float[]{1.0f, 0.2f, 0.8f};
            case ELDRITCH -> new float[]{0.4f, 0.0f, 0.0f};
            case PHYSICAL -> new float[]{0.7f, 0.7f, 0.7f};
            default -> new float[]{1.0f, 1.0f, 1.0f};
        };
    }

    private static void renderArcSegment(PoseStack.Pose p, BufferBuilder buffer, float cx, float cy, float cz,
                                         float a1, float a2, float r1, float r2, float r, float g, float b, float a) {
        float x1 = cx + (float) Math.cos(a1) * r1;
        float z1 = cz + (float) Math.sin(a1) * r1;
        float x2 = cx + (float) Math.cos(a2) * r1;
        float z2 = cz + (float) Math.sin(a2) * r1;
        
        float x3 = cx + (float) Math.cos(a1) * r2;
        float z3 = cz + (float) Math.sin(a1) * r2;
        float x4 = cx + (float) Math.cos(a2) * r2;
        float z4 = cz + (float) Math.sin(a2) * r2;

        // Quad 1: (x1,z1) -> (x2,z2) -> (x3,z3)
        buffer.vertex(p.pose(), x1, cy, z1).color(r, g, b, a).endVertex();
        buffer.vertex(p.pose(), x2, cy, z2).color(r, g, b, a).endVertex();
        buffer.vertex(p.pose(), x3, cy, z3).color(r, g, b, a).endVertex();

        // Quad 2: (x2,z2) -> (x4,z4) -> (x3,z3)
        buffer.vertex(p.pose(), x2, cy, z2).color(r, g, b, a).endVertex();
        buffer.vertex(p.pose(), x4, cy, z4).color(r, g, b, a).endVertex();
        buffer.vertex(p.pose(), x3, cy, z3).color(r, g, b, a).endVertex();
    }
}
