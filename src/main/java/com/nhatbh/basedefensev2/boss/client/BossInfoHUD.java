package com.nhatbh.basedefensev2.boss.client;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 2D Boss HUD has been removed in favor of {@link com.nhatbh.basedefensev2.client.render.BossOverheadWorldRenderer}.
 */
@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BossInfoHUD {

    private static final int MARGIN_TOP = 28;

    public static int getBottomY() {
        return MARGIN_TOP;
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        // Disabled: 2D Boss HUD removed in favor of Overhead Render
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        // Disabled: 2D Boss HUD removed in favor of Overhead Render
    }
}
