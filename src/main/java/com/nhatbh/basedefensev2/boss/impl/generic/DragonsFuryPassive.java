package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.skills.PassiveSkill;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import com.nhatbh.basedefensev2.registry.ModEffects;
import com.nhatbh.basedefensev2.strength.EntityStrengthData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.nhatbh.basedefensev2.utils.UUIDHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class DragonsFuryPassive implements PassiveSkill {

    private static final Map<LivingEntity, DragonsFuryPassive> ACTIVE_INSTANCES = new WeakHashMap<>();

    private float rage = 0.0f;
    private long lastHitGameTime = 0;
    private final Map<UUID, Long> playerHitCooldowns = new HashMap<>();

    private boolean triggered75 = false;
    private boolean triggered50 = false;
    private boolean triggered25 = false;

    private boolean wasEnraged = false;
    private boolean desperationActive = false;

    private VengeanceActive activeVengeance = null;

    private int barrageTimer = 0;
    private final String lesserDragonEntityId;
    private final List<LivingEntity> spawnedLesserDragons = new ArrayList<>();
    private final List<ScorchGroundInstance> activeScorchGrounds = new ArrayList<>();
    private final List<FieryBarrageInstance> activeBarrages = new ArrayList<>();

    private static final UUID SPEED_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("dragons_fury", "speed");
    private static final UUID ATTACK_SPEED_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("dragons_fury",
            "attack_speed");
    private static final UUID DAMAGE_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("dragons_fury", "damage");
    private static final UUID STRENGTH_TAKEN_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("dragons_fury",
            "strength_taken");
    private static final UUID SPECIAL_STRENGTH_TAKEN_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("dragons_fury",
            "special_strength_taken");

    public DragonsFuryPassive() {
        this("minecraft:phantom");
    }

    public DragonsFuryPassive(String lesserDragonEntityId) {
        this.lesserDragonEntityId = lesserDragonEntityId;
    }

    public static DragonsFuryPassive get(LivingEntity boss) {
        return ACTIVE_INSTANCES.get(boss);
    }

    @Override
    public void onAdded(LivingEntity boss) {
        ACTIVE_INSTANCES.put(boss, this);
    }

    @Override
    public void onRemoved(LivingEntity boss) {
        ACTIVE_INSTANCES.remove(boss);
        onEnrageEnd(boss);
        removeDesperationBuffs(boss);
    }

    @Override
    public void tick(LivingEntity boss) {
        if (boss.level().isClientSide() || !boss.isAlive())
            return;

        long currentTime = boss.level().getGameTime();

        // 1. Threshold Check (75%, 50%, 25%)
        checkThresholds(boss);

        // 2. Tick Vengeance if active
        if (activeVengeance != null) {
            if (!activeVengeance.tick()) {
                activeVengeance = null;
            }
        }

        // 3. Rage Decay Engine
        if (desperationActive) {
            rage = 200.0f;
            if (vulnerabilityTimer > 0) {
                vulnerabilityTimer--;
                if (vulnerabilityTimer == 0) {
                    removeVulnerability(boss);
                }
            }
        } else {
            if (currentTime - lastHitGameTime >= 100) { // 5s without taking damage
                rage = Math.max(0.0f, rage - 0.5f); // 10/s (0.5 per tick)
            }
        }

        // 4. Check Enrage State Transition
        boolean currentlyEnraged = isEnraged();
        if (currentlyEnraged && !wasEnraged) {
            onEnrageStart(boss);
        } else if (!currentlyEnraged && wasEnraged) {
            onEnrageEnd(boss);
        }
        wasEnraged = currentlyEnraged;

        // 5. Enrage Shroud & Burn Ring Ticks
        if (currentlyEnraged) {
            tickEnragedState(boss);
        } else {
            // Baseline rage visual glow
            if (boss.level() instanceof ServerLevel serverLevel && rage > 0) {
                double speed = (rage / 200.0) * 0.1;
                serverLevel.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5,
                        boss.getZ(),
                        (int) (rage / 20), 0.3, 0.5, 0.3, speed);
            }
        }

        // 5. Threshold 1 (75% HP) — Fiery Barrage Ticker
        if (triggered75) {
            int interval = (vulnerabilityTimer > 0) ? 120 : 80;
            barrageTimer++;
            if (barrageTimer >= interval) {
                barrageTimer = 0;
                launchFieryBarrage(boss);
            }
        }

        // 6. Threshold 2 (50% HP) — Dragon's Call
        if (triggered50 && !spawnedLesserDragons.isEmpty()) {
            spawnedLesserDragons.removeIf(dragon -> !dragon.isAlive());
        }

        // 7. Tick Projectiles & Scorch Ground Patches
        tickActiveBarrages(boss);
        tickActiveScorchGrounds(boss);
    }

    private int vulnerabilityTimer = 0;
    private static final UUID VULNERABILITY_STRENGTH_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("dragons_fury", "vulnerability_strength");

    public void triggerPostSkillVulnerability(LivingEntity boss) {
        vulnerabilityTimer = 120; // 6 seconds (120 ticks)

        // Remove strength damage immunity and add +300% strength damage taken (+2.0 multiplier base = 300% total)
        var attr = boss.getAttribute(com.nhatbh.basedefensev2.strength.ModAttributes.STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get());
        if (attr != null) {
            attr.removeModifier(STRENGTH_TAKEN_MOD_UUID);
            if (attr.getModifier(VULNERABILITY_STRENGTH_MOD_UUID) == null) {
                attr.addTransientModifier(new AttributeModifier(VULNERABILITY_STRENGTH_MOD_UUID,
                        "PostSkillVulnerability", 2.0, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }

        if (boss.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.7f);
        }
    }

    private void removeVulnerability(LivingEntity boss) {
        var attr = boss.getAttribute(com.nhatbh.basedefensev2.strength.ModAttributes.STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get());
        if (attr != null) {
            attr.removeModifier(VULNERABILITY_STRENGTH_MOD_UUID);
            if (attr.getModifier(STRENGTH_TAKEN_MOD_UUID) == null) {
                attr.addTransientModifier(new AttributeModifier(STRENGTH_TAKEN_MOD_UUID, "EnragedRawStrengthImmunity", -1.0,
                        AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
    }

    public boolean isVulnerable() {
        return vulnerabilityTimer > 0;
    }

    public boolean isEnraged() {
        return rage >= 100.0f || desperationActive;
    }

    public boolean isDesperationActive() {
        return desperationActive;
    }

    public float getRage() {
        return rage;
    }

    public void onBossHit(LivingEntity attacker) {
        if (desperationActive)
            return;

        UUID attackerId = attacker.getUUID();
        long currentTime = attacker.level().getGameTime();
        long lastHit = playerHitCooldowns.getOrDefault(attackerId, 0L);

        if (currentTime - lastHit >= 5) {
            playerHitCooldowns.put(attackerId, currentTime);
            rage = Math.min(200.0f, rage + 5.0f);
            lastHitGameTime = currentTime;
        }
    }

    public void onShieldBreak() {
        if (!desperationActive) {
            rage = 0.0f;
        }
    }

    private void checkThresholds(LivingEntity boss) {
        float hpPercent = boss.getHealth() / boss.getMaxHealth();

        if (!triggered75 && hpPercent <= 0.75f) {
            triggered75 = true;
            activeVengeance = new VengeanceActive(boss);
        }

        if (!triggered50 && hpPercent <= 0.50f) {
            triggered50 = true;
            activeVengeance = new VengeanceActive(boss);
            spawnLesserDragons(boss);
        }

        if (!triggered25 && hpPercent <= 0.25f) {
            triggered25 = true;
            desperationActive = true;
            rage = 200.0f;
            activeVengeance = new VengeanceActive(boss);
            applyDesperationBuffs(boss);
        }
    }

    private void onEnrageStart(LivingEntity boss) {
        var attr = boss
                .getAttribute(com.nhatbh.basedefensev2.strength.ModAttributes.STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get());
        if (attr != null && attr.getModifier(STRENGTH_TAKEN_MOD_UUID) == null) {
            attr.addTransientModifier(new AttributeModifier(STRENGTH_TAKEN_MOD_UUID, "EnragedRawStrengthImmunity", -1.0,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }

        var specialAttr = boss.getAttribute(
                com.nhatbh.basedefensev2.strength.ModAttributes.SPECIAL_STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get());
        if (specialAttr != null && specialAttr.getModifier(SPECIAL_STRENGTH_TAKEN_MOD_UUID) == null) {
            specialAttr.addTransientModifier(new AttributeModifier(SPECIAL_STRENGTH_TAKEN_MOD_UUID,
                    "EnragedSpecialStrengthBoost", 1.0, AttributeModifier.Operation.MULTIPLY_BASE));
        }

        if (boss.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.5f, 0.8f);
            serverLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5f, 1.2f);
        }
    }

    private void onEnrageEnd(LivingEntity boss) {
        var attr = boss
                .getAttribute(com.nhatbh.basedefensev2.strength.ModAttributes.STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get());
        if (attr != null) {
            attr.removeModifier(STRENGTH_TAKEN_MOD_UUID);
            attr.removeModifier(VULNERABILITY_STRENGTH_MOD_UUID);
        }

        var specialAttr = boss.getAttribute(
                com.nhatbh.basedefensev2.strength.ModAttributes.SPECIAL_STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get());
        if (specialAttr != null) {
            specialAttr.removeModifier(SPECIAL_STRENGTH_TAKEN_MOD_UUID);
        }
    }
    private void tickEnragedState(LivingEntity boss) {
        if (!(boss.level() instanceof ServerLevel serverLevel))
            return;

        boolean vulnerable = vulnerabilityTimer > 0;
        Vec3 center = boss.position();
        double height = boss.getBbHeight();
        double radius = boss.getBbWidth() * 0.8;
        long time = serverLevel.getGameTime();

        // Double Helix Flame Pillar rising around boss
        for (int i = 0; i < 2; i++) {
            double angle = (time * 0.15) + (i * Math.PI);
            double py = boss.getY() + ((time * 0.05 + i * 0.5) % height);
            double px = boss.getX() + Math.cos(angle) * radius;
            double pz = boss.getZ() + Math.sin(angle) * radius;

            if (vulnerable) {
                // Blue Crit / Shattered Flames when vulnerable
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);
                serverLevel.sendParticles(ParticleTypes.CRIT, px, py, pz, 2, 0.05, 0.05, 0.05, 0.1);
            } else {
                // Intense Dragon Enrage: Flame + Smoke Burst
                serverLevel.sendParticles(ParticleTypes.FLAME, px, py, pz, 2, 0.03, 0.05, 0.03, 0.02);
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, px, py, pz, 1, 0.01, 0.02, 0.01, 0.01);
            }
        }

        // 2. Heavy Ground Pressure Aura (Flame Ring with Lava Sparks)
        double burnRingRadius = 6.0;
        int ringParticles = vulnerable ? 8 : 12;
        for (int i = 0; i < ringParticles; i++) {
            double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
            double r = Math.sqrt(serverLevel.random.nextDouble()) * burnRingRadius;
            double px = center.x + r * Math.cos(angle);
            double pz = center.z + r * Math.sin(angle);
            double py = center.y + 0.1;

            if (vulnerable) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0.02, 0.02, 0.02, 0.005);
            } else {
                serverLevel.sendParticles(ParticleTypes.FLAME, px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);
                if (serverLevel.random.nextInt(4) == 0) {
                    serverLevel.sendParticles(ParticleTypes.LAVA, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }

        // 3. Vulnerability Shockwave burst indicator
        if (vulnerable && time % 5 == 0) {
            ParticleUtils.renderCircle(serverLevel, ParticleTypes.SOUL_FIRE_FLAME, center.add(0, 0.2, 0), radius + 0.5, 12, 0.05);
        }

        // Burn Ring Tick (every 10 ticks = 0.5s)
        if (boss.level().getGameTime() % 10 == 0) {
            AABB ringBox = new AABB(center.x - burnRingRadius - 2, center.y - 2, center.z - burnRingRadius - 2,
                    center.x + burnRingRadius + 2, center.y + 4, center.z + burnRingRadius + 2);
            List<Player> players = serverLevel.getEntitiesOfClass(Player.class, ringBox);

            DamageSource burnSource = serverLevel.damageSources().inFire();

            for (Player player : players) {
                if (!player.isAlive() || player.isCreative() || player.isSpectator())
                    continue;

                double distSq = player.distanceToSqr(boss);
                if (distSq <= burnRingRadius * burnRingRadius) {
                    // Inside ring: 1.5% max HP / sec (0.75% per 10 ticks)
                    player.hurt(burnSource, player.getMaxHealth() * 0.0075f);
                    player.getPersistentData().putBoolean("DragonsFury_InsideRing", true);

                    // Apply Healing Block while inside
                    if (ModEffects.HEALING_BLOCK.isPresent()) {
                        player.addEffect(new MobEffectInstance(ModEffects.HEALING_BLOCK.get(), 15, 0, false, false));
                    }
                } else {
                    // Just exited ring: apply Seared (50% heal debuff for 5s)
                    if (player.getPersistentData().getBoolean("DragonsFury_InsideRing")) {
                        player.getPersistentData().remove("DragonsFury_InsideRing");
                        if (ModEffects.SEARED.isPresent()) {
                            player.addEffect(new MobEffectInstance(ModEffects.SEARED.get(), 100, 0, false, true));
                        }
                    }
                }
            }
        }
    }

    private void launchFieryBarrage(LivingEntity boss) {
        if (!(boss.level() instanceof ServerLevel serverLevel))
            return;

        Vec3 center = boss.position();
        AABB searchBox = new AABB(center.x - 32, center.y - 8, center.z - 32, center.x + 32, center.y + 8,
                center.z + 32);
        List<Player> targets = serverLevel.getEntitiesOfClass(Player.class, searchBox,
                p -> p.isAlive() && !p.isCreative() && p.distanceToSqr(boss) >= 16.0); // Exclude within 4m

        if (targets.isEmpty())
            return;

        Player target = targets.get(serverLevel.random.nextInt(targets.size()));

        // Spawn invisible ArmorStand as projectile
        ArmorStand projectile = EntityType.ARMOR_STAND.create(serverLevel);
        if (projectile != null) {
            projectile.setPos(boss.getX(), boss.getY() + boss.getBbHeight() * 0.8, boss.getZ());
            projectile.setInvisible(true);
            projectile.setNoGravity(true);
            serverLevel.addFreshEntity(projectile);

            activeBarrages.add(new FieryBarrageInstance(projectile, target.position(), boss));

            serverLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5f, 0.8f);
        }
    }

    private void tickActiveBarrages(LivingEntity boss) {
        if (!(boss.level() instanceof ServerLevel serverLevel))
            return;

        Iterator<FieryBarrageInstance> it = activeBarrages.iterator();
        while (it.hasNext()) {
            FieryBarrageInstance barrage = it.next();
            if (!barrage.tick(serverLevel)) {
                it.remove();
            }
        }
    }

    private void tickActiveScorchGrounds(LivingEntity boss) {
        if (!(boss.level() instanceof ServerLevel serverLevel))
            return;

        Iterator<ScorchGroundInstance> it = activeScorchGrounds.iterator();
        while (it.hasNext()) {
            ScorchGroundInstance scorch = it.next();
            if (!scorch.tick(serverLevel, boss)) {
                it.remove();
            }
        }
    }

    private void spawnLesserDragons(LivingEntity boss) {
        if (!(boss.level() instanceof ServerLevel serverLevel))
            return;

        ResourceLocation loc = ResourceLocation.parse(lesserDragonEntityId);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(loc);
        if (type == null)
            return;

        for (int i = 0; i < 2; i++) {
            double angle = (i * Math.PI) + (serverLevel.random.nextDouble() * 0.5);
            double spawnX = boss.getX() + Math.cos(angle) * 16.0;
            double spawnZ = boss.getZ() + Math.sin(angle) * 16.0;

            Entity entity = type.create(serverLevel);
            if (entity instanceof LivingEntity dragon) {
                dragon.setPos(spawnX, boss.getY() + 4.0, spawnZ);
                serverLevel.addFreshEntity(dragon);
                spawnedLesserDragons.add(dragon);
            }
        }
    }

    private void applyDesperationBuffs(LivingEntity boss) {
        var atts = boss.getAttributes();

        var speedAttr = atts.getInstance(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getModifier(SPEED_MOD_UUID) == null) {
            speedAttr.addTransientModifier(new AttributeModifier(SPEED_MOD_UUID, "DesperationSpeed", 0.30,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }

        var atkSpeedAttr = atts.getInstance(Attributes.ATTACK_SPEED);
        if (atkSpeedAttr != null && atkSpeedAttr.getModifier(ATTACK_SPEED_MOD_UUID) == null) {
            atkSpeedAttr.addTransientModifier(new AttributeModifier(ATTACK_SPEED_MOD_UUID, "DesperationAtkSpeed", 0.30,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }

        var damageAttr = atts.getInstance(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null && damageAttr.getModifier(DAMAGE_MOD_UUID) == null) {
            damageAttr.addTransientModifier(new AttributeModifier(DAMAGE_MOD_UUID, "DesperationDamage", 0.50,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private void removeDesperationBuffs(LivingEntity boss) {
        var atts = boss.getAttributes();
        var speed = atts.getInstance(Attributes.MOVEMENT_SPEED);
        if (speed != null)
            speed.removeModifier(SPEED_MOD_UUID);

        var atkSpeed = atts.getInstance(Attributes.ATTACK_SPEED);
        if (atkSpeed != null)
            atkSpeed.removeModifier(ATTACK_SPEED_MOD_UUID);

        var damage = atts.getInstance(Attributes.ATTACK_DAMAGE);
        if (damage != null)
            damage.removeModifier(DAMAGE_MOD_UUID);
    }

    // Inner Helper Classes for Projectiles & Scorch Ground
    private class FieryBarrageInstance {
        private final ArmorStand stand;
        private final Vec3 targetPos;
        private final Vec3 startPos;
        private final LivingEntity boss;
        private int ticks = 0;
        private static final int TOTAL_TICKS = 30; // 1.5s flight

        public FieryBarrageInstance(ArmorStand stand, Vec3 targetPos, LivingEntity boss) {
            this.stand = stand;
            this.targetPos = targetPos;
            this.startPos = stand.position();
            this.boss = boss;
        }

        public boolean tick(ServerLevel level) {
            if (!stand.isAlive())
                return false;

            ticks++;
            double progress = (double) ticks / TOTAL_TICKS;
            if (progress >= 1.0) {
                onImpact(level, targetPos);
                stand.discard();
                return false;
            }

            // Arc motion towards target
            Vec3 currentPos = startPos.add(targetPos.subtract(startPos).scale(progress)).add(0,
                    Math.sin(progress * Math.PI) * 3.0, 0);
            stand.setPos(currentPos.x, currentPos.y, currentPos.z);

            // Fire particles
            level.sendParticles(ParticleTypes.FLAME, currentPos.x, currentPos.y, currentPos.z, 5, 0.1, 0.1, 0.1, 0.02);
            level.sendParticles(ParticleTypes.LAVA, currentPos.x, currentPos.y, currentPos.z, 1, 0.05, 0.05, 0.05, 0.0);

            // Check collision with players
            AABB hitBox = new AABB(currentPos.x - 1, currentPos.y - 1, currentPos.z - 1, currentPos.x + 1,
                    currentPos.y + 1, currentPos.z + 1);
            List<Player> players = level.getEntitiesOfClass(Player.class, hitBox,
                    p -> !p.isCreative() && !p.isSpectator());
            if (!players.isEmpty()) {
                Player hitPlayer = players.get(0);
                hitPlayer.hurt(level.damageSources().inFire(), hitPlayer.getMaxHealth() * 0.20f);
                if (ModEffects.SEARED.isPresent()) {
                    hitPlayer.addEffect(new MobEffectInstance(ModEffects.SEARED.get(), 40, 0, false, true));
                }
                onImpact(level, currentPos);
                stand.discard();
                return false;
            }

            return true;
        }

        private void onImpact(ServerLevel level, Vec3 impactPos) {
            level.playSound(null, impactPos.x, impactPos.y, impactPos.z,
                    SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 1.5f, 1.2f);
            level.sendParticles(ParticleTypes.EXPLOSION, impactPos.x, impactPos.y, impactPos.z, 2, 0.2, 0.2, 0.2, 0.0);

            // Spawn Scorch Ground if impact is at least 3m from boss
            if (boss.isAlive() && boss.position().distanceToSqr(impactPos) >= 9.0) {
                ArmorStand scorchStand = EntityType.ARMOR_STAND.create(level);
                if (scorchStand != null) {
                    scorchStand.setPos(impactPos.x, impactPos.y, impactPos.z);
                    scorchStand.setInvisible(true);
                    scorchStand.setNoGravity(true);
                    level.addFreshEntity(scorchStand);

                    activeScorchGrounds.add(new ScorchGroundInstance(scorchStand));
                }
            }
        }
    }

    private class ScorchGroundInstance {
        private final ArmorStand stand;
        private int ticksRemaining = 100; // 5s duration

        public ScorchGroundInstance(ArmorStand stand) {
            this.stand = stand;
        }

        public boolean tick(ServerLevel level, LivingEntity boss) {
            if (!stand.isAlive())
                return false;

            ticksRemaining--;
            if (ticksRemaining <= 0) {
                stand.discard();
                return false;
            }

            Vec3 pos = stand.position();

            // Particles
            ParticleUtils.renderCircle(level, ParticleTypes.FLAME, pos.add(0, 0.1, 0), 3.0, 16, 0.02);
            if (ticksRemaining % 5 == 0) {
                level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.1, pos.z, 2, 1.0, 0.1, 1.0, 0.0);
            }

            // Damage & Heal Block inside Scorch (4% max HP/s = 0.2% per tick or 2% every 10
            // ticks)
            if (ticksRemaining % 10 == 0) {
                AABB box = new AABB(pos.x - 3, pos.y - 1, pos.z - 3, pos.x + 3, pos.y + 3, pos.z + 3);
                List<Player> players = level.getEntitiesOfClass(Player.class, box, p -> p.distanceToSqr(stand) <= 9.0);

                for (Player player : players) {
                    if (!player.isAlive() || player.isCreative() || player.isSpectator())
                        continue;
                    player.hurt(level.damageSources().inFire(), player.getMaxHealth() * 0.02f);
                    if (ModEffects.HEALING_BLOCK.isPresent()) {
                        player.addEffect(new MobEffectInstance(ModEffects.HEALING_BLOCK.get(), 15, 0, false, false));
                    }
                }
            }

            return true;
        }
    }
}
