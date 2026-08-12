package com.nhatbh.basedefensev2.ai;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.api.event.PoiseDamageEvent;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles mob target switching mechanics using entity NBT persistence:
 * 1. Target change timer: Starts a 5-second (100 ticks) timer on EVERY target change, but only restricts Threat Switch & Gunfire Alerts.
 * 2. Threat switch: 3-second window (60 ticks) tracking combined (health + poise) damage requiring >= 40% max health.
 * 3. Gunfire alert switch: Unsilenced TacZ gunfire taunts hostile mobs (Enemy) with NO current target (subject to the 5s target change timer).
 */
@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobTargetingEventHandler {

    private static final long TARGET_CHANGE_COOLDOWN_TICKS = 100L; // 5 seconds
    private static final long DAMAGE_WINDOW_TICKS = 60L; // 3 seconds
    private static final float AGGRO_THREAT_DAMAGE_PERCENT = 0.40f; // 40% max HP threshold

    // NBT Keys stored on mob.getPersistentData()
    private static final String KEY_LAST_TARGET_CHANGE = "bdv2_last_target_change_time";
    private static final String KEY_THREAT_ATTACKER = "bdv2_threat_attacker";
    private static final String KEY_THREAT_DAMAGE = "bdv2_threat_damage";
    private static final String KEY_THREAT_LAST_HIT = "bdv2_threat_last_hit_time";

    /**
     * Rule 1: Starts a 5-second timer on EVERY target change.
     * Note: This listener ONLY records the timestamp and does NOT cancel vanilla/natural target changes.
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }

        LivingEntity newTarget = event.getNewTarget();
        LivingEntity currentTarget = mob.getTarget();

        // Whenever target changes to a new target, update timer & clear threat NBT
        if (newTarget != null && newTarget != currentTarget) {
            CompoundTag nbt = mob.getPersistentData();
            nbt.putLong(KEY_LAST_TARGET_CHANGE, mob.level().getGameTime());
            clearThreatNBT(nbt);
        }
    }

    /**
     * Rule 2a: Health damage threat tracking.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getSource().getEntity() instanceof LivingEntity attacker) {
            addThreat(mob, attacker, event.getAmount(), mob.level().getGameTime());
        }
    }

    /**
     * Rule 2b: Poise damage threat tracking.
     */
    @SubscribeEvent
    public static void onPoiseDamage(PoiseDamageEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getAttacker() != null) {
            addThreat(mob, event.getAttacker(), event.getAmount(), mob.level().getGameTime());
        }
    }

    /**
     * System 1: Threat Switch
     * Accumulates threat (health + poise damage) from an attacker in NBT and switches target if threshold is met.
     * Restricted by the 5-second target change timer.
     */
    private static void addThreat(Mob mob, LivingEntity attacker, float threatAmount, long gameTime) {
        if (mob.level().isClientSide || threatAmount <= 0 || attacker == mob) {
            return;
        }

        // If attacker is already targeted, no switch needed
        if (mob.getTarget() == attacker) {
            return;
        }

        CompoundTag nbt = mob.getPersistentData();

        // Restricted by the 5-second target change timer if mob has an active living target
        long lastChangeTime = nbt.getLong(KEY_LAST_TARGET_CHANGE);
        if (mob.getTarget() != null && mob.getTarget().isAlive() && (lastChangeTime > 0 && gameTime - lastChangeTime < TARGET_CHANGE_COOLDOWN_TICKS)) {
            return; // Locked out by 5-second target change timer
        }

        String attackerUUIDStr = attacker.getUUID().toString();
        String storedAttacker = nbt.getString(KEY_THREAT_ATTACKER);
        long lastHitTime = nbt.getLong(KEY_THREAT_LAST_HIT);
        float threatAccumulated = nbt.getFloat(KEY_THREAT_DAMAGE);

        // Reset tracking if new attacker or window of 3 seconds expired
        if (!attackerUUIDStr.equals(storedAttacker) || (gameTime - lastHitTime > DAMAGE_WINDOW_TICKS)) {
            threatAccumulated = threatAmount;
        } else {
            threatAccumulated += threatAmount;
        }

        // Save updated threat data in NBT
        nbt.putString(KEY_THREAT_ATTACKER, attackerUUIDStr);
        nbt.putLong(KEY_THREAT_LAST_HIT, gameTime);
        nbt.putFloat(KEY_THREAT_DAMAGE, threatAccumulated);

        // Threshold check: 40% max health
        float requiredThreat = mob.getMaxHealth() * AGGRO_THREAT_DAMAGE_PERCENT;
        if (threatAccumulated >= requiredThreat) {
            mob.setTarget(attacker);
        }
    }

    /**
     * System 2: TacZ Gunfire Alert Switch
     * Firing an unsilenced TacZ gun taunts nearby HOSTILE mobs (Enemy) with NO current target (32 block radius),
     * subject to the 5-second target change timer.
     */
    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) {
            return;
        }

        LivingEntity shooter = event.getShooter();
        if (shooter == null || shooter.level().isClientSide) {
            return;
        }

        ItemStack gunStack = event.getGunItemStack();
        if (isSilenced(gunStack)) {
            return; // Silenced gun: no noise alert
        }

        long gameTime = shooter.level().getGameTime();
        double alertRadius = 32.0;
        AABB searchBox = shooter.getBoundingBox().inflate(alertRadius);

        for (Mob mob : shooter.level().getEntitiesOfClass(Mob.class, searchBox)) {
            if (mob == shooter || !mob.isAlive()) {
                continue;
            }

            // Only taunt hostile mobs (Enemy interface)
            if (!(mob instanceof Enemy)) {
                continue;
            }

            // Only taunt hostile mobs with NO current living target
            if (mob.getTarget() != null && mob.getTarget().isAlive()) {
                continue;
            }

            CompoundTag nbt = mob.getPersistentData();
            long lastChangeTime = nbt.getLong(KEY_LAST_TARGET_CHANGE);

            // Restricted by the 5-second target change timer
            if (lastChangeTime > 0 && (gameTime - lastChangeTime < TARGET_CHANGE_COOLDOWN_TICKS)) {
                continue; // Cannot switch target yet
            }

            mob.setTarget(shooter);
        }
    }

    private static void clearThreatNBT(CompoundTag nbt) {
        nbt.remove(KEY_THREAT_ATTACKER);
        nbt.remove(KEY_THREAT_DAMAGE);
        nbt.remove(KEY_THREAT_LAST_HIT);
    }

    /**
     * Checks if a TacZ gun is silenced using TacZ's native GunProperties.SILENCE evaluation.
     */
    private static boolean isSilenced(ItemStack gunStack) {
        if (gunStack == null || gunStack.isEmpty()) {
            return false;
        }

        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) {
            return false;
        }

        ResourceLocation gunId = iGun.getGunId(gunStack);
        CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (gunIndex == null) {
            return false;
        }

        // Evaluate all attachment modifiers on the gun stack natively through TacZ
        AttachmentCacheProperty cacheProperty = new AttachmentCacheProperty();
        cacheProperty.eval(gunStack, gunIndex.getGunData());

        Pair<Integer, Boolean> silenceData = cacheProperty.getCache(GunProperties.SILENCE);
        return silenceData != null && Boolean.TRUE.equals(silenceData.right());
    }
}
