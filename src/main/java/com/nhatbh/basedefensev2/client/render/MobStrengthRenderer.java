package com.nhatbh.basedefensev2.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nhatbh.basedefensev2.BaseDefenseMod;
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
public class MobStrengthRenderer {

    private static final Minecraft MC = Minecraft.getInstance();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }

        if (MC.level == null)
            return;

        boolean hasAnyStrength = false;
        for (Entity e : MC.level.entitiesForRendering()) {
            if (!e.isAlive() || !(e instanceof net.minecraft.world.entity.LivingEntity living))
                continue;
            com.nhatbh.basedefensev2.strength.EntityStrengthData data = com.nhatbh.basedefensev2.strength.EntityStrengthData.get(living);
            if (data != null && (data.currentStrength > 0 || data.recoveryTicks > 0)) {
                hasAnyStrength = true;
                break;
            }
        }

        if (!hasAnyStrength)
            return;

        PoseStack pose = event.getPoseStack();
        Camera cam = event.getCamera();
        Vec3 camPos = cam.getPosition();

        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        // 1. Setup OpenGL state for drawing a generic translucent filled shape
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull(); // Makes the circle visible from above and below
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        // 2. Begin drawing TRIANGLES instead of lines
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (Entity e : MC.level.entitiesForRendering()) {
            if (!e.isAlive() || !(e instanceof net.minecraft.world.entity.LivingEntity living))
                continue;

            com.nhatbh.basedefensev2.strength.EntityStrengthData data = com.nhatbh.basedefensev2.strength.EntityStrengthData.get(living);
            if (data == null)
                continue;

            float currentStrength = data.currentStrength;
            float maxStrength = data.maxStrength;
            int recoveryTicks = data.recoveryTicks;

            if (currentStrength <= 0 && recoveryTicks <= 0)
                continue;

            renderFilledStrengthCircle(pose, buffer, e, currentStrength, maxStrength, recoveryTicks);
        }

        // 3. Draw the batch to the screen
        tesselator.end();

        // 4. Restore OpenGL state
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        pose.popPose();
    }

    private static void renderFilledStrengthCircle(PoseStack pose, BufferBuilder buffer, Entity entity,
            float currentStrength, float maxStrength, int recoveryTicks) {
        float partialTicks = MC.getFrameTime();
        double x = entity.xOld + (entity.getX() - entity.xOld) * partialTicks;
        double y = entity.yOld + (entity.getY() - entity.yOld) * partialTicks + 0.05; // Slightly above ground
        double z = entity.zOld + (entity.getZ() - entity.zOld) * partialTicks;

        float percent;
        float r, g, b;

        if (currentStrength <= 0 && recoveryTicks > 0) {
            // Exhausted mode: render gray circle filling up
            // 300 ticks is max, so 300 -> 0% fill, 0 -> 100% fill
            percent = 1.0f - (recoveryTicks / 300.0f);
            percent = Math.min(1.0f, Math.max(0.0f, percent));
            r = 0.5f;
            g = 0.5f;
            b = 0.5f;
        } else {
            percent = Math.min(1.0f, Math.max(0.0f, currentStrength / maxStrength));
            // Color interpolation: White (100%) -> Yellow (50%) -> Red (0%)
            r = 1.0f;
            if (percent > 0.5f) {
                g = 1.0f;
                b = (percent - 0.5f) * 2.0f;
            } else {
                g = percent * 2.0f;
                b = 0.0f;
            }
        }

        // Lowered alpha to 0.5f so the filled area is semi-transparent and doesn't
        // block the view of the ground
        float a = 0.5f;

        float radius = entity.getBbWidth() * 0.8f;
        if (radius < 0.5f)
            radius = 0.5f;

        int totalSegments = 36;
        int activeSegments = (int) Math.ceil(totalSegments * percent);
        float step = (float) (2 * Math.PI / totalSegments);

        PoseStack.Pose p = pose.last();
        float cx = (float) x;
        float cy = (float) y;
        float cz = (float) z;

        // Draw a triangle for each active segment
        for (int i = 0; i < activeSegments; i++) {
            float a1 = i * step;
            float a2 = (i + 1) * step;

            float px1 = cx + (float) Math.cos(a1) * radius;
            float pz1 = cz + (float) Math.sin(a1) * radius;
            float px2 = cx + (float) Math.cos(a2) * radius;
            float pz2 = cz + (float) Math.sin(a2) * radius;

            // Triangle setup: Center -> Point 1 -> Point 2
            buffer.vertex(p.pose(), cx, cy, cz).color(r, g, b, a).endVertex();
            buffer.vertex(p.pose(), px1, cy, pz1).color(r, g, b, a).endVertex();
            buffer.vertex(p.pose(), px2, cy, pz2).color(r, g, b, a).endVertex();
        }
    }
}
