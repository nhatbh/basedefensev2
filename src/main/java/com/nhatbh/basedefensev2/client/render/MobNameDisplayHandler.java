package com.nhatbh.basedefensev2.client.render;

import com.nhatbh.basedefensev2.api.PoiseAPI;
import com.nhatbh.basedefensev2.boss.client.BossResourceBarRegistry;
import com.nhatbh.basedefensev2.boss.core.BossComponent;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.elemental.MobElementService;
import com.nhatbh.basedefensev2.level.MobLevelData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "basedefensev2", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobNameDisplayHandler {

    private static final Minecraft MC = Minecraft.getInstance();

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }

        if (BossManager.isBoss(living)) {
            event.setResult(Event.Result.DENY);
            return;
        }

        if (!(living instanceof Enemy)) {
            return;
        }

        // Raycast check: Only render when looking directly at the mob or boss
        HitResult hitResult = MC.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            event.setResult(Event.Result.DENY);
            return;
        }

        Entity targetedEntity = ((EntityHitResult) hitResult).getEntity();
        if (targetedEntity != living) {
            event.setResult(Event.Result.DENY);
            return;
        }

        // 1. Level Color (Lighter to darker as level increases)
        int level = MobLevelData.getLevel(living);
        String levelColor = getLevelColor(level);
        String levelPrefix = "§7[" + levelColor + "Lv." + level + "§7] ";

        // 2. Element Icon (Icon only, element color)
        ElementType element = MobElementService.getElement(living);
        String elementIcon = element.getColorCode() + element.getIcon() + " ";

        // 3. HP Display (Red text with ❤ icon, numbers only)
        int currentHp = Math.round(living.getHealth());
        int maxHp = Math.round(living.getMaxHealth());
        String hpString = " §c❤ " + currentHp + "/" + maxHp;

        // 4. Poise / Strength Display (White text with 🛡 icon, numbers only; recovery time when broken)
        String poiseString = "";
        if (PoiseAPI.hasPoise(living)) {
            float currentPoise = PoiseAPI.getCurrentPoise(living);
            float maxPoise = PoiseAPI.getMaxPoise(living);

            if (currentPoise <= 0 || PoiseAPI.isExhausted(living)) {
                int recoveryTicks = PoiseAPI.getRecoveryTicks(living);
                float recoverySeconds = recoveryTicks / 20.0f;
                poiseString = " §e🛡 " + String.format("%.1fs", recoverySeconds);
            } else {
                poiseString = " §f🛡 " + (int) currentPoise + "/" + (int) maxPoise;
            }
        }

        MutableComponent formattedName = Component.literal(levelPrefix + elementIcon)
                .append(Component.literal(hpString + poiseString));

        event.setContent(formattedName);
        event.setResult(Event.Result.ALLOW);
    }

    /**
     * Color progression from lighter to darker:
     * Lv 1-10:   §f (White - Lightest)
     * Lv 11-25:  §e (Yellow)
     * Lv 26-45:  §6 (Gold / Light Orange)
     * Lv 46-70:  §c (Light Red / Pinkish)
     * Lv 71-100: §4 (Dark Red)
     * Lv >100:   §5 (Dark Purple / Darkest)
     */
    private static String getLevelColor(int level) {
        if (level <= 10) return "§f";
        if (level <= 25) return "§e";
        if (level <= 45) return "§6";
        if (level <= 70) return "§c";
        if (level <= 100) return "§4";
        return "§5";
    }
}
