package com.nhatbh.basedefensev2.sanctity.client;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.sanctity.network.ClientReviveData;
import com.nhatbh.basedefensev2.sanctity.network.RescueRequestPacket;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, value = Dist.CLIENT)
public class ClientInputHandler {
    private static Integer currentTargetId = null;
    private static int giveUpHoldTicks = 0;
    private static final int GIVE_UP_REQUIRED_TICKS = 40; // Hold for 2 seconds (40 ticks)

    public static Integer getCurrentTargetId() { return currentTargetId; }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Player localPlayer = mc.player;
        if (localPlayer == null || mc.level == null) return;

        ClientReviveData.setLocalPlayerId(localPlayer.getId());

        boolean isHoldingRightDown = mc.options.keyUse.isDown() || 
            GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        Integer foundTargetId = null;
        
        // If we are already rescuing, stay on that target as long as we hold the button and are close
        if (currentTargetId != null) {
            if (isHoldingRightDown) {
                Entity currentTarget = mc.level.getEntity(currentTargetId);
                if (currentTarget != null && localPlayer.distanceToSqr(currentTarget) <= 25.0) { // 5 blocks leniency
                    foundTargetId = currentTargetId;
                }
            }
        }
        
        // If we don't have a target yet, look for one
        if (foundTargetId == null && isHoldingRightDown) {
            HitResult crosshairTarget = mc.hitResult;
            if (crosshairTarget != null && crosshairTarget.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHit = (EntityHitResult) crosshairTarget;
                if (entityHit.getEntity() instanceof Player targetPlayer) {
                    if (localPlayer.distanceToSqr(targetPlayer) <= 16.0) {
                        foundTargetId = targetPlayer.getId();
                    }
                }
            }
        }

        // Handle State Change
        if (foundTargetId != currentTargetId) {
            // Stop old rescue
            if (currentTargetId != null) {
                NetworkManager.INSTANCE.sendToServer(new RescueRequestPacket(currentTargetId, false));
            }
            
            // Start new rescue
            if (foundTargetId != null) {
                NetworkManager.INSTANCE.sendToServer(new RescueRequestPacket(foundTargetId, true));
            }
            
            currentTargetId = foundTargetId;
        }

        // Handle Give Up Key (Hold I key while knocked down)
        if (ClientReviveData.isKnockedDown()) {
            boolean isHoldingGiveUpKey = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_I) == GLFW.GLFW_PRESS;
            if (isHoldingGiveUpKey) {
                giveUpHoldTicks++;
                if (giveUpHoldTicks >= GIVE_UP_REQUIRED_TICKS) {
                    NetworkManager.INSTANCE.sendToServer(new com.nhatbh.basedefensev2.sanctity.network.GiveUpPacket());
                    giveUpHoldTicks = 0;
                }
            } else {
                giveUpHoldTicks = 0;
            }
        } else {
            giveUpHoldTicks = 0;
        }
    }

    public static int getGiveUpHoldTicks() {
        return giveUpHoldTicks;
    }

    public static int getGiveUpRequiredTicks() {
        return GIVE_UP_REQUIRED_TICKS;
    }
}
