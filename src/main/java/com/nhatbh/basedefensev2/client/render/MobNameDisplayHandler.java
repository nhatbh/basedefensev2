package com.nhatbh.basedefensev2.client.render;

import com.nhatbh.basedefensev2.api.PoiseAPI;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.elemental.MobElementService;
import com.nhatbh.basedefensev2.level.MobLevelData;
import com.nhatbh.basedefensev2.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "basedefensev2", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobNameDisplayHandler {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final double MAX_RAYCAST_DISTANCE = 32.0D; // Extended raycast distance (32 blocks)

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }

        if (BossManager.isBoss(living)) {
            event.setResult(Event.Result.DENY);
            return;
        }

        if (living instanceof Player || living instanceof ArmorStand) {
            return;
        }

        // Extended raycast check (32 blocks range)
        if (!isPlayerLookingAt(living)) {
            event.setResult(Event.Result.DENY);
            return;
        }

        // 1. Level Formatting (7-Tier Chroma & Obsidian Gradient)
        int level = MobLevelData.getLevel(living);
        String levelPrefix = formatLevelPrefix(level);

        // 2. Element Icon (Icon only, element color - only for hostile mobs/bosses)
        ElementType element = MobElementService.getElement(living);
        String elementIcon = element != null ? element.getColorCode() + element.getIcon() + " " : "";

        // 3. HP Display (Red text with ❤ icon, numbers only)
        int currentHp = Math.round(living.getHealth());
        int maxHp = Math.round(living.getMaxHealth());
        String hpString = "§c❤ " + currentHp + "/" + maxHp;

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

        // 5. Corrosion Display (§6🔰 -X% tag when armor is corroded)
        String corrosionTag = "";
        int corrosionHits = living.getPersistentData().getInt("bdv2_corrosion_hits");
        if (corrosionHits > 0) {
            double baseArmor = living.getArmorValue();
            double mult = PoiseAPI.calculateCorrosionMultiplier(corrosionHits, baseArmor);
            int reductionPercent = (int) Math.round((1.0 - mult) * 100.0);
            if (reductionPercent > 0) {
                corrosionTag = " §6🔰 -" + reductionPercent + "%";
            }
        }

        // 6. Suppression Display (§b[SUPPRESSED] cyan tag)
        String suppressionTag = (ModEffects.SUPPRESSION.isPresent() && living.hasEffect(ModEffects.SUPPRESSION.get())) ? " §b[SUPPRESSED]" : "";

        MutableComponent formattedName = Component.literal(levelPrefix + elementIcon + hpString + poiseString + corrosionTag + suppressionTag);

        event.setContent(formattedName);
        event.setResult(Event.Result.ALLOW);
    }

    private static boolean isPlayerLookingAt(LivingEntity entity) {
        if (MC.player == null) return false;

        Vec3 eyePos = MC.player.getEyePosition(1.0F);
        Vec3 lookVec = MC.player.getViewVector(1.0F);
        Vec3 reachVec = eyePos.add(lookVec.x * MAX_RAYCAST_DISTANCE, lookVec.y * MAX_RAYCAST_DISTANCE, lookVec.z * MAX_RAYCAST_DISTANCE);

        AABB searchBox = MC.player.getBoundingBox().expandTowards(lookVec.scale(MAX_RAYCAST_DISTANCE)).inflate(2.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                MC.player,
                eyePos,
                reachVec,
                searchBox,
                target -> target == entity && !target.isSpectator() && target.isPickable(),
                MAX_RAYCAST_DISTANCE * MAX_RAYCAST_DISTANCE
        );

        return hit != null && hit.getEntity() == entity;
    }

    /**
     * 7-Tier Chroma & Obsidian Gradient Level Display:
     * - Lv 1–19:   §7[§fLv.X§7]  (White level text, Light Gray brackets)
     * - Lv 20–39:  §7[§eLv.X§7]  (Bright Yellow level text)
     * - Lv 40–59:  §7[§6Lv.X§7]  (Deep Gold level text)
     * - Lv 60–79:  §7[§cLv.X§7]  (Crimson Red level text)
     * - Lv 80–99:  §4[§4§lLv.X§4]  (Bold Dark Red level text & brackets)
     * - Lv 100–149:§5[§5§lLv.X§5]  (Bold Deep Purple level text & brackets)
     * - Lv 150+:   §d☠ §0§l[Lv.X] §d☠  (Bold Obsidian Black level tag flanked by Magenta Skulls)
     */
    private static String formatLevelPrefix(int level) {
        if (level < 20) {
            return "§7[§fLv." + level + "§7] ";
        } else if (level < 40) {
            return "§7[§eLv." + level + "§7] ";
        } else if (level < 60) {
            return "§7[§6Lv." + level + "§7] ";
        } else if (level < 80) {
            return "§7[§cLv." + level + "§7] ";
        } else if (level < 100) {
            return "§4[§4§lLv." + level + "§4] ";
        } else if (level < 150) {
            return "§5[§5§lLv." + level + "§5] ";
        } else {
            return "§d☠ §0§l[Lv." + level + "] §d☠ ";
        }
    }
}
