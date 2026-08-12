package com.nhatbh.basedefensev2.boss.impl.generic.passive.dragonsfury;

import com.nhatbh.basedefensev2.boss.skills.PassiveSkill;
import com.nhatbh.basedefensev2.effects.SuffocationEffect;
import com.nhatbh.basedefensev2.registry.ModEffects;
import com.nhatbh.basedefensev2.utils.UUIDHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class DragonsFuryPassive implements PassiveSkill {

    private static final Map<LivingEntity, DragonsFuryPassive> ACTIVE_INSTANCES = new WeakHashMap<>();

    private float rage = 0.0f;
    private int fieryCounterattackCooldown = 0; // Ticks remaining for 2-min cooldown (2400 ticks)
    private boolean isCounterattackChanneling = false;
    private int channelTicks = 0; // 0 to 100 ticks (5 seconds)

    private boolean desperationActive = false;
    private boolean wasEnraged = false;

    private static final UUID SPEED_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("dragons_fury", "despiration_speed");
    private static final UUID ATTACK_SPEED_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("dragons_fury", "despiration_atk_speed");
    private static final UUID DAMAGE_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("dragons_fury", "despiration_damage");

    public DragonsFuryPassive() {
    }

    public static DragonsFuryPassive get(LivingEntity boss) {
        return ACTIVE_INSTANCES.get(boss);
    }

    @Override
    public String getName() {
        return "Long Nộ";
    }

    @Override
    public String getTitlePrefix() {
        return "Draconic";
    }

    @Override
    public String getDescription() {
        return "Kiếm phong chạm vảy, nộ khí hóa bích. Huyết mạch càng cạn kiệt, ác long càng thâm nhập ma đạo, trút cơn cuồng nộ diệt thế thuở mạt lộ.";
    }

    @Override
    public void onAdded(LivingEntity boss) {
        ACTIVE_INSTANCES.put(boss, this);
        com.nhatbh.basedefensev2.boss.client.BossResourceBarRegistry.registerBar(
            boss,
            () -> isCounterattackChanneling ? "FIERY COUNTERATTACK" : (isEnraged() ? "ENRAGED RAGE" : "Rage"),
            () -> this.rage,
            () -> 200.0f,
            () -> isCounterattackChanneling ? 0xFFFF0000 : (isEnraged() ? 0xFFFF4500 : 0xFFFFAA00),
            () -> isCounterattackChanneling ? 0xFF800000 : (isEnraged() ? 0xFF8B0000 : 0xFF884400)
        );
    }

    @Override
    public void onRemoved(LivingEntity boss) {
        ACTIVE_INSTANCES.remove(boss);
        com.nhatbh.basedefensev2.boss.client.BossResourceBarRegistry.unregisterBar(boss);
        removeDesperationBuffs(boss);
    }

    @Override
    public void tick(LivingEntity boss) {
        if (boss.level().isClientSide() || !boss.isAlive())
            return;

        ServerLevel level = (ServerLevel) boss.level();

        // 1. Tick Cooldowns
        if (fieryCounterattackCooldown > 0) {
            fieryCounterattackCooldown--;
        }

        // 2. Check Phase 2 Desperation (HP <= 25%)
        float hpPercent = boss.getHealth() / boss.getMaxHealth();
        if (!desperationActive && hpPercent <= 0.25f) {
            desperationActive = true;
            rage = Math.max(100.0f, rage);
            applyDesperationBuffs(boss);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.5f, 0.7f);
        }

        if (desperationActive) {
            rage = Math.max(100.0f, rage); // Lock rage at 100+ minimum
        }

        // 3. Handle Fiery Counterattack Channeling (At 200 Rage)
        if (rage >= 200.0f && !isCounterattackChanneling && fieryCounterattackCooldown <= 0) {
            startCounterattackChannel(boss, level);
        }

        if (isCounterattackChanneling) {
            tickCounterattackChannel(boss, level);
            return; // Skip normal movement & ticks during channel
        }

        // 4. Check Enrage State Transition
        boolean currentlyEnraged = isEnraged();
        if (currentlyEnraged && !wasEnraged) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0f, 0.9f);
        }
        wasEnraged = currentlyEnraged;

        // 5. Air Burning & Suffocation (100+ Rage)
        if (currentlyEnraged) {
            tickAirBurning(boss, level);
        } else if (rage > 0) {
            // Idle Flame particles proportional to Rage
            double speed = (rage / 200.0) * 0.05;
            level.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5,
                    boss.getZ(), (int) (rage / 20), 0.3, 0.4, 0.3, speed);
        }
    }

    public boolean isEnraged() {
        return rage >= 100.0f || desperationActive;
    }

    public boolean isDesperationActive() {
        return desperationActive;
    }

    public boolean isCounterattackChanneling() {
        return isCounterattackChanneling;
    }

    public float getRage() {
        return rage;
    }

    public void onBossHit(LivingEntity attacker) {
        if (isCounterattackChanneling)
            return;

        float maxCap = (fieryCounterattackCooldown > 0) ? 150.0f : 200.0f;
        rage = Math.min(maxCap, rage + 5.0f);
    }

    public void onShieldBreak() {
        if (!desperationActive) {
            rage = 0.0f;
        }
    }

    // --- Fiery Counterattack Channeling Mechanics ---
    private void startCounterattackChannel(LivingEntity boss, ServerLevel level) {
        isCounterattackChanneling = true;
        channelTicks = 0;

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 0.6f);
    }

    private void tickCounterattackChannel(LivingEntity boss, ServerLevel level) {
        channelTicks++;

        // Make boss immobile during channel
        boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
        if (boss instanceof Mob mob) {
            mob.getNavigation().stop();
        }

        Vec3 bossCenter = boss.position().add(0, boss.getBbHeight() * 0.5, 0);

        // 1. Particle Pull Effect (flame particles velocity vector drawing into boss)
        for (int i = 0; i < 12; i++) {
            double angle = level.random.nextDouble() * 2 * Math.PI;
            double radius = 2.0 + level.random.nextDouble() * 8.0; // 2m to 10m radius
            double px = bossCenter.x + Math.cos(angle) * radius;
            double pz = bossCenter.z + Math.sin(angle) * radius;
            double py = bossCenter.y + (level.random.nextDouble() - 0.5) * 2.0;

            Vec3 particlePos = new Vec3(px, py, pz);
            Vec3 pullVel = bossCenter.subtract(particlePos).normalize().scale(0.35);

            level.sendParticles(ParticleTypes.FLAME, px, py, pz, 1, pullVel.x, pullVel.y, pullVel.z, 0.15);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.LAVA, px, py, pz, 1, pullVel.x, pullVel.y, pullVel.z, 0.05);
            }
        }

        // 2. Escapable Pulling Velocity for players in 10-block range
        AABB pullArea = new AABB(bossCenter.x - 10, bossCenter.y - 4, bossCenter.z - 10,
                bossCenter.x + 10, bossCenter.y + 6, bossCenter.z + 10);
        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, pullArea,
                p -> p.isAlive() && com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.canBeHitBySkill(p));

        for (Player p : nearbyPlayers) {
            double dist = p.distanceTo(boss);
            if (dist <= 10.0 && dist > 0.5) {
                Vec3 toBoss = bossCenter.subtract(p.position()).normalize().scale(0.18);
                p.setDeltaMovement(p.getDeltaMovement().add(toBoss.x, 0.02, toBoss.z));
                p.hurtMarked = true; // Sync velocity to client
            }
        }

        // 3. Channeling Complete (5 seconds / 100 ticks) -> Release Flame Explosion!
        if (channelTicks >= 100) {
            detonateCounterattack(boss, level, bossCenter);
        }
    }

    private void detonateCounterattack(LivingEntity boss, ServerLevel level, Vec3 center) {
        isCounterattackChanneling = false;
        channelTicks = 0;
        rage = 0.0f;
        fieryCounterattackCooldown = 2400; // 2-minute cooldown (120s = 2400 ticks)

        // Explosive Sound & Visuals
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.5f, 0.5f);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 3.0f, 0.8f);

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 5, 1.0, 1.0, 1.0, 0);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 50, 2.0, 1.5, 2.0, 0.3);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 80, 2.5, 2.0, 2.5, 0.4);

        // 4-Block Radius Lethal Flame Explosion
        AABB blastArea = new AABB(center.x - 4, center.y - 2, center.z - 4, center.x + 4, center.y + 4, center.z + 4);
        List<Player> hitPlayers = level.getEntitiesOfClass(Player.class, blastArea,
                p -> p.isAlive() && com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.canBeHitBySkill(p));

        for (Player p : hitPlayers) {
            double dist = p.distanceTo(boss);
            if (dist <= 4.0) {
                // Lethal proximity scaling: 100% max HP at center (0-1m), 40% at 4m edge
                float distRatio = Math.max(0.0f, Math.min(1.0f, (float) (dist / 4.0)));
                float dmgPercent = 1.0f - (distRatio * 0.60f); // 1.0 down to 0.4

                float damage = p.getMaxHealth() * dmgPercent;
                p.hurt(level.damageSources().inFire(), damage);

                // Knockback away from boss center
                Vec3 pushDir = p.position().subtract(center).normalize();
                if (pushDir.lengthSqr() < 0.001) {
                    pushDir = new Vec3(0, 1, 0);
                }
                p.setDeltaMovement(pushDir.x * 2.2, 0.7, pushDir.z * 2.2);
                p.hurtMarked = true;
            }
        }
    }

    // --- Air Burning & Suffocation Mechanics (100+ Rage) ---
    private void tickAirBurning(LivingEntity boss, ServerLevel level) {
        Vec3 center = boss.position();
        double radius = 6.0;

        // Visual burning air particles
        if (boss.tickCount % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = level.random.nextDouble() * 2 * Math.PI;
                double r = Math.sqrt(level.random.nextDouble()) * radius;
                double px = center.x + r * Math.cos(angle);
                double pz = center.z + r * Math.sin(angle);
                double py = center.y + 0.1 + level.random.nextDouble() * boss.getBbHeight();

                level.sendParticles(ParticleTypes.FLAME, px, py, pz, 1, 0.01, 0.02, 0.01, 0.01);
                if (level.random.nextInt(6) == 0) {
                    level.sendParticles(ParticleTypes.SMOKE, px, py, pz, 1, 0, 0.05, 0, 0.01);
                }
            }
        }

        // Apply Grievous Wounds (Healing Block) & Suffocation every 1s (20 ticks)
        if (boss.tickCount % 20 == 0) {
            AABB auraArea = new AABB(center.x - radius, center.y - 2, center.z - radius,
                    center.x + radius, center.y + 4, center.z + radius);
            List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, auraArea,
                    p -> p.isAlive() && p.distanceToSqr(boss) <= radius * radius &&
                         com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.canBeHitBySkill(p));

            for (Player p : nearbyPlayers) {
                // Apply Grievous Wounds (Healing Block)
                if (ModEffects.HEALING_BLOCK.isPresent()) {
                    p.addEffect(new MobEffectInstance(ModEffects.HEALING_BLOCK.get(), 30, 0, false, false));
                }

                // Apply / Upgrade Suffocation Effect (adds +1 stack per second inside cloud)
                SuffocationEffect.applyOrUpgrade(p);
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
}
