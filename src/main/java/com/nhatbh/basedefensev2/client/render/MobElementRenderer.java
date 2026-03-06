package com.nhatbh.basedefensev2.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.nhatbh.basedefensev2.boss.core.AbstractBossEntity;
import com.nhatbh.basedefensev2.elemental.ElementType;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = "basedefensev2", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobElementRenderer {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final String ELEMENT_NBT_KEY = "ElementType";
    private static final double MAX_RENDER_DISTANCE_SQ = 4096.0D; // 64 blocks squared

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }

        if (MC.level == null || MC.player == null) {
            return;
        }

        // Early exit check - see if any entity has an element
        boolean hasAnyElement = false;
        for (Entity e : MC.level.entitiesForRendering()) {
            if (!e.isAlive())
                continue;
            if (!(e instanceof Enemy) && !(e instanceof AbstractBossEntity))
                continue;

            String elementTypeStr = e.getPersistentData().getString(ELEMENT_NBT_KEY);
            ElementType element = ElementType.fromString(elementTypeStr);
            if (element != null && element != ElementType.PHYSICAL) {
                hasAnyElement = true;
                break;
            }
        }

        if (!hasAnyElement) {
            return;
        }

        Camera camera = MC.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Font font = MC.font;
        PoseStack poseStack = event.getPoseStack();

        MultiBufferSource.BufferSource bufferSource = MultiBufferSource
                .immediate(Tesselator.getInstance().getBuilder());

        boolean drewAnything = false;

        for (Entity entity : MC.level.entitiesForRendering()) {
            if (!entity.isAlive())
                continue;
            if (!(entity instanceof Enemy) && !(entity instanceof AbstractBossEntity))
                continue;
            if (entity.distanceToSqr(MC.player) > MAX_RENDER_DISTANCE_SQ)
                continue;

            String elementTypeStr = entity.getPersistentData().getString(ELEMENT_NBT_KEY);
            ElementType elementType = ElementType.fromString(elementTypeStr);

            if (elementType == null || elementType == ElementType.PHYSICAL)
                continue;

            ElementData data = getElementData(elementType);
            if (data == null)
                continue;

            poseStack.pushPose();

            // Calculate interpolated position relative to the camera
            double x = Mth.lerp(event.getPartialTick(), entity.xo, entity.getX()) - cameraPos.x;
            double y = Mth.lerp(event.getPartialTick(), entity.yo, entity.getY()) - cameraPos.y;
            double z = Mth.lerp(event.getPartialTick(), entity.zo, entity.getZ()) - cameraPos.z;

            // Adjust for mob height - position above head
            float heightOffset = entity.getBbHeight() + 0.5f;
            if (entity.hasCustomName()) {
                heightOffset += 0.3f;
            }
            // Add extra offset if mob has strength data (to not overlap with strength
            // indicator)
            if (entity.getPersistentData().contains("MobStrengthData")) {
                heightOffset += 0.25f;
            }

            // Move the matrix to the spot above the mob's head
            poseStack.translate(x, y + heightOffset, z);

            // Billboarding: Force the matrix to copy the camera's rotation
            poseStack.mulPose(camera.rotation());

            // Scale down to vanilla nametag size and flip so text isn't backwards
            poseStack.scale(-0.025F, -0.025F, 0.025F);

            Matrix4f matrix4f = poseStack.last().pose();
            float textWidth = font.width(data.text);
            float xOffset = -textWidth / 2.0F;

            int light = MC.getEntityRenderDispatcher().getPackedLightCoords(entity, event.getPartialTick());

            // Draw the text with semi-transparent background
            font.drawInBatch(
                    data.text,
                    xOffset,
                    0,
                    data.color,
                    false,
                    matrix4f,
                    bufferSource,
                    Font.DisplayMode.NORMAL,
                    0x40000000, // 25% opacity black background
                    light);

            poseStack.popPose();
            drewAnything = true;
        }

        if (drewAnything) {
            bufferSource.endBatch();
        }
    }

    private static ElementData getElementData(ElementType elementType) {
        return switch (elementType) {
            case FIRE -> new ElementData("FIRE", 0xFFFF5555);
            case ICE -> new ElementData("ICE", 0xFF55FFFF);
            case LIGHTNING -> new ElementData("LIGHTNING", 0xFFFFFF55);
            case NATURE -> new ElementData("NATURE", 0xFF55FF55);
            case AQUA -> new ElementData("AQUA", 0xFF00AAFF);
            case HOLY -> new ElementData("HOLY", 0xFFFFFFAA);
            case EVOCATION -> new ElementData("EVOCATION", 0xFFFF55FF);
            case ENDER -> new ElementData("ENDER", 0xFFAA00AA);
            case ELDRITCH -> new ElementData("ELDRITCH", 0xFF00AA00);
            case BLOOD -> new ElementData("BLOOD", 0xFFAA0000);
            default -> null;
        };
    }

    private record ElementData(String text, int color) {
    }
}
