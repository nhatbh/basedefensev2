package com.nhatbh.basedefensev2.boss.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "basedefensev2", value = Dist.CLIENT)
public class RenderUtils {

    private static final List<Telegraph> activeTelegraphs = new ArrayList<>();

    public static void addTelegraph(Vec3 pos, TelegraphShape shape, double arg1, double arg2, int durationTicks, int color, float maxAlpha) {
        activeTelegraphs.add(new Telegraph(pos, shape, arg1, arg2, durationTicks, color, maxAlpha));
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        
        PoseStack poseStack = event.getPoseStack();

        // Tessellator logic to draw shapes goes here:
        // Use RenderSystem to set up buffers and colors based on Telegraph data
        
        activeTelegraphs.removeIf(Telegraph::tick);
    }

    public enum TelegraphShape {
        CIRCLE, RING, CONE, LINE
    }

    public static class Telegraph {
        public final Vec3 position;
        public final TelegraphShape shape;
        public final double arg1; 
        public final double arg2; 
        public final int maxTicks;
        public final int color;
        public final float maxAlpha;
        
        public int ticksLived = 0;

        public Telegraph(Vec3 position, TelegraphShape shape, double arg1, double arg2, int maxTicks, int color, float maxAlpha) {
            this.position = position;
            this.shape = shape;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.maxTicks = maxTicks;
            this.color = color;
            this.maxAlpha = maxAlpha;
        }

        public boolean tick() {
            ticksLived++;
            return ticksLived >= maxTicks;
        }
    }
}
