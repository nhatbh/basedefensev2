package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.client.BossResourceBarRegistry;
import com.nhatbh.basedefensev2.boss.skills.PassiveSkill;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import com.nhatbh.basedefensev2.utils.UUIDHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class OverclockPassive implements PassiveSkill {

    private static final Map<LivingEntity, OverclockPassive> ACTIVE_INSTANCES = new WeakHashMap<>();

    private float charge = 0.0f; // 0 to 100
    private Vec3 lastBossPos = null;
    private double accumulatedDistance = 0.0;

    private boolean isSupercharged = false;
    private int superchargedTimer = 0; // Ticks remaining in supercharged state
    private int thermalFrictionTimer = 0; // Ice interaction speed negation timer (ticks)

    private boolean triggered75 = false; // Phase 2
    private boolean triggered50 = false; // Phase 3
    private boolean triggered25 = false; // Phase 4

    private int conductorTimer = 0;
    private int tetherTimer = 0;
    private int strikeTimer = 0;

    private final StaticConductorManager conductorManager = new StaticConductorManager();
    private final List<VoltaicTetherInstance> activeTethers = new ArrayList<>();
    private final List<StaticTrailInstance> activeTrails = new ArrayList<>();
    private final List<LightningStrikeTelegraph> activeStrikes = new ArrayList<>();

    private static final UUID CHARGE_SPEED_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("overclock", "charge_speed");
    private static final UUID SUPERCHARGED_SPEED_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("overclock", "super_speed");
    private static final UUID SUPERCHARGED_ATTACK_SPEED_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("overclock", "super_attack_speed");

    public static OverclockPassive get(LivingEntity boss) {
        return ACTIVE_INSTANCES.get(boss);
    }

    @Override
    public void onAdded(LivingEntity boss) {
        ACTIVE_INSTANCES.put(boss, this);
        this.lastBossPos = boss.position();

        // Register HUD bar for Overclock Charge
        BossResourceBarRegistry.registerBar(
                boss,
                () -> isSupercharged ? "OVERCLOCK [SUPERCHARGED]" : "Kinetic Charge",
                () -> charge,
                () -> 100.0f,
                () -> isSupercharged ? 0xFF00E5FF : 0xFFFFAA00,
                () -> isSupercharged ? 0xFF0055FF : 0xFF884400
        );
    }

    @Override
    public void onRemoved(LivingEntity boss) {
        ACTIVE_INSTANCES.remove(boss);
        BossResourceBarRegistry.unregisterBar(boss);
        removeModifiers(boss);
        conductorManager.clear();
        activeTethers.clear();
        activeTrails.clear();
        activeStrikes.clear();
    }

    @Override
    public void tick(LivingEntity boss) {
        if (boss.level().isClientSide() || !boss.isAlive()) return;

        ServerLevel level = (ServerLevel) boss.level();

        // 1. Phase Threshold Checks
        checkPhaseThresholds(boss);

        // 2. Kinetic Charge Engine - Movement Distance Tracking
        if (lastBossPos != null) {
            double moved = boss.position().distanceTo(lastBossPos);
            accumulatedDistance += moved;
            if (accumulatedDistance >= 5.0) {
                int points = (int) (accumulatedDistance / 5.0) * 5;
                accumulatedDistance %= 5.0;
                addCharge(boss, points);
            }
        }
        lastBossPos = boss.position();

        // Enforcement of Phase 4 Charge floor
        if (triggered25 && charge < 50.0f) {
            charge = 50.0f;
        }

        // 3. Update Scaling Speed Modifier (+0.3% MS per point of Charge)
        updateChargeSpeedModifier(boss);

        // 4. Handle Supercharged State & Buffs
        if (isSupercharged) {
            superchargedTimer--;

            // Tick Thermal Friction (Ice negation)
            if (thermalFrictionTimer > 0) {
                thermalFrictionTimer--;
                removeSuperchargedSpeedModifier(boss);
            } else {
                applySuperchargedSpeedModifier(boss);
            }

            // Tick Static Aura (3-block ticking damage)
            if (boss.level().getGameTime() % 10 == 0) {
                tickStaticAura(level, boss);
            }

            // Leave Static Trail along boss path
            if (boss.level().getGameTime() % 5 == 0 && boss.getDeltaMovement().lengthSqr() > 0.001) {
                spawnStaticTrail(level, boss.position());
            }

            // End of Supercharged State
            if (superchargedTimer <= 0) {
                endSuperchargedState(boss);
            }
        }

        // 5. Timed Mechanics: Conductors, Tethers, Lightning Strikes
        tickTimedMechanics(level, boss);

        // 6. Tick Sub-Managers & Instances
        conductorManager.tick(level, boss);
        tickActiveTethers();
        tickActiveTrails(level);
        tickActiveStrikes(level);
    }

    public void addCharge(LivingEntity boss, float amount) {
        if (isSupercharged) return;

        float floor = triggered25 ? 50.0f : 0.0f;
        charge = Math.min(100.0f, Math.max(floor, charge + amount));

        if (charge >= 100.0f) {
            startSuperchargedState(boss);
        }
    }

    public void drainCharge(float amount) {
        // Phase 4: Water interactions no longer drain charge
        if (triggered25) return;

        charge = Math.max(0.0f, charge - amount);
    }

    public boolean isSupercharged() {
        return isSupercharged;
    }

    /**
     * Triggered by Water Skill hit on Supercharged boss.
     * Conduction Surge: 6-block radial water splash, clears Static Trails, drains 25 Charge.
     */
    public void triggerConductionSurge(LivingEntity boss) {
        if (!isSupercharged || !(boss.level() instanceof ServerLevel level)) return;

        Vec3 center = boss.position();

        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.0f, 1.2f);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 1.5f, 1.5f);

        // 12-block radial water & spark splash (doubled from 6)
        ParticleUtils.renderCircle(level, ParticleTypes.SPLASH, center, 12.0, 48, 0.2);
        ParticleUtils.renderCircle(level, ParticleTypes.ELECTRIC_SPARK, center, 12.0, 48, 0.1);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 1.0, center.z, 5, 0.8, 0.8, 0.8, 0.05);

        // Clear all static trails instantly
        activeTrails.clear();

        // Drain 25 Charge (handled with Phase 4 protection inside drainCharge)
        drainCharge(25.0f);

        // Instant burst lightning damage to anyone caught in melee range (within 12 blocks, doubled from 6)
        List<Player> meleePlayers = HitboxUtils.getEntitiesInCircle(
                level, Player.class, center, 12.0, Player::isAlive);

        for (Player p : meleePlayers) {
            float burstDmg = p.getMaxHealth() * 0.15f + 3.0f;
            p.hurt(level.damageSources().lightningBolt(), burstDmg);
        }
    }

    /**
     * Triggered by Ice Skill hit on Supercharged boss.
     * Thermal Friction: Negates +30% MS bonus for 3 seconds (60 ticks).
     */
    public void triggerThermalFriction(LivingEntity boss) {
        if (!isSupercharged) return;

        this.thermalFrictionTimer = 60; // 3 seconds
        removeSuperchargedSpeedModifier(boss);

        if (boss.level() instanceof ServerLevel level) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 1.5f);
            level.sendParticles(ParticleTypes.SNOWFLAKE, boss.getX(), boss.getY() + 1.0, boss.getZ(), 20, 0.5, 0.8, 0.5, 0.1);
        }
    }

    private void checkPhaseThresholds(LivingEntity boss) {
        float hpPercent = boss.getHealth() / boss.getMaxHealth();

        if (!triggered75 && hpPercent <= 0.75f) {
            triggered75 = true;
        }

        if (!triggered50 && hpPercent <= 0.50f) {
            triggered50 = true;
        }

        if (!triggered25 && hpPercent <= 0.25f) {
            triggered25 = true;
            charge = Math.max(50.0f, charge);
        }
    }

    private void startSuperchargedState(LivingEntity boss) {
        isSupercharged = true;
        superchargedTimer = triggered25 ? 300 : 200; // 15s in P4, 10s default

        applySuperchargedSpeedModifier(boss);
        applySuperchargedAttackSpeedModifier(boss);

        if (boss.level() instanceof ServerLevel level) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.5f, 0.8f);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1.0, boss.getZ(), 1, 0, 0, 0, 0);
        }
    }

    private void endSuperchargedState(LivingEntity boss) {
        isSupercharged = false;
        superchargedTimer = 0;
        charge = triggered25 ? 50.0f : 0.0f;

        removeSuperchargedSpeedModifier(boss);
        removeSuperchargedAttackSpeedModifier(boss);
    }

    private void updateChargeSpeedModifier(LivingEntity boss) {
        var attr = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;

        double bonus = (charge / 100.0) * 0.30; // +0.3% per point = up to +30%
        attr.removeModifier(CHARGE_SPEED_MOD_UUID);

        if (bonus > 0) {
            attr.addTransientModifier(new AttributeModifier(
                    CHARGE_SPEED_MOD_UUID, "OverclockChargeSpeed", bonus, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private void applySuperchargedSpeedModifier(LivingEntity boss) {
        var attr = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && attr.getModifier(SUPERCHARGED_SPEED_MOD_UUID) == null) {
            attr.addTransientModifier(new AttributeModifier(
                    SUPERCHARGED_SPEED_MOD_UUID, "SuperchargedSpeed", 0.30, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private void removeSuperchargedSpeedModifier(LivingEntity boss) {
        var attr = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(SUPERCHARGED_SPEED_MOD_UUID);
        }
    }

    private void applySuperchargedAttackSpeedModifier(LivingEntity boss) {
        var attr = boss.getAttribute(Attributes.ATTACK_SPEED);
        if (attr != null && attr.getModifier(SUPERCHARGED_ATTACK_SPEED_MOD_UUID) == null) {
            attr.addTransientModifier(new AttributeModifier(
                    SUPERCHARGED_ATTACK_SPEED_MOD_UUID, "SuperchargedAttackSpeed", 0.25, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private void removeSuperchargedAttackSpeedModifier(LivingEntity boss) {
        var attr = boss.getAttribute(Attributes.ATTACK_SPEED);
        if (attr != null) {
            attr.removeModifier(SUPERCHARGED_ATTACK_SPEED_MOD_UUID);
        }
    }

    private void removeModifiers(LivingEntity boss) {
        var speed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(CHARGE_SPEED_MOD_UUID);
            speed.removeModifier(SUPERCHARGED_SPEED_MOD_UUID);
        }
        var atkSpeed = boss.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpeed != null) {
            atkSpeed.removeModifier(SUPERCHARGED_ATTACK_SPEED_MOD_UUID);
        }
    }

    private void tickStaticAura(ServerLevel level, LivingEntity boss) {
        Vec3 center = boss.position();

        ParticleUtils.renderCircle(level, ParticleTypes.ELECTRIC_SPARK, center, 6.0, 24, 0.05);

        List<Player> nearbyPlayers = HitboxUtils.getEntitiesInCircle(
                level, Player.class, center, 6.0, Player::isAlive);

        Vec3 bossBody = boss.position().add(0, 1.0, 0);
        for (Player p : nearbyPlayers) {
            p.hurt(level.damageSources().lightningBolt(), p.getMaxHealth() * 0.01f + 0.5f);
            Vec3 playerBody = p.position().add(0, 1.0, 0);
            ParticleUtils.renderLine(level, ParticleTypes.ELECTRIC_SPARK, bossBody, playerBody, 0.3, 0.02);
        }
    }

    private void spawnStaticTrail(ServerLevel level, Vec3 pos) {
        activeTrails.add(new StaticTrailInstance(pos));
    }

    private void tickTimedMechanics(ServerLevel level, LivingEntity boss) {
        // Voltaic Tether (Every 18 seconds = 360 ticks)
        tetherTimer++;
        if (tetherTimer >= 360) {
            tetherTimer = 0;
            spawnVoltaicTethers(level, boss);
        }

        // Static Conductors (Every 45 seconds = 900 ticks in Phase 2+)
        if (triggered75) {
            conductorTimer++;
            if (conductorTimer >= 900) {
                conductorTimer = 0;
                spawnConductorNearPlayer(level, boss);
            }
        }

        // Targeted Lightning Strikes (Every 20 seconds = 400 ticks in Phase 3+)
        if (triggered50) {
            strikeTimer++;
            if (strikeTimer >= 400) {
                strikeTimer = 0;
                spawnLightningStrikes(level, boss);
            }
        }
    }

    private void spawnVoltaicTethers(ServerLevel level, LivingEntity boss) {
        List<Player> players = level.getEntitiesOfClass(Player.class,
                boss.getBoundingBox().inflate(32), Player::isAlive);

        if (players.size() < 2) return;

        // Sort by distance to boss
        players.sort(Comparator.comparingDouble(boss::distanceToSqr));

        int pairsToSpawn = triggered50 ? 2 : 1; // 2 pairs in P3+, 1 pair in P1-P2

        for (int i = 0; i < pairsToSpawn && (i * 2 + 1) < players.size(); i++) {
            Player p1 = players.get(i * 2);
            Player p2 = players.get(i * 2 + 1);
            activeTethers.add(new VoltaicTetherInstance(p1, p2, boss, triggered25));
        }
    }

    private void spawnConductorNearPlayer(ServerLevel level, LivingEntity boss) {
        List<Player> players = level.getEntitiesOfClass(Player.class,
                boss.getBoundingBox().inflate(32), Player::isAlive);

        if (players.isEmpty()) return;

        Player target = players.get(level.random.nextInt(players.size()));
        Vec3 spawnPos = target.position().add((level.random.nextDouble() - 0.5) * 4.0, 0, (level.random.nextDouble() - 0.5) * 4.0);

        conductorManager.spawnObelisk(level, spawnPos);
    }

    private void spawnLightningStrikes(ServerLevel level, LivingEntity boss) {
        List<Player> players = level.getEntitiesOfClass(Player.class,
                boss.getBoundingBox().inflate(32), Player::isAlive);

        if (players.isEmpty()) return;

        Collections.shuffle(players);
        int count = Math.min(2, players.size());

        for (int i = 0; i < count; i++) {
            activeStrikes.add(new LightningStrikeTelegraph(players.get(i).position()));
        }
    }

    private void tickActiveTethers() {
        activeTethers.removeIf(tether -> !tether.tick());
    }

    private void tickActiveTrails(ServerLevel level) {
        Iterator<StaticTrailInstance> it = activeTrails.iterator();
        while (it.hasNext()) {
            StaticTrailInstance trail = it.next();
            if (!trail.tick(level)) {
                it.remove();
            }
        }
    }

    private void tickActiveStrikes(ServerLevel level) {
        Iterator<LightningStrikeTelegraph> it = activeStrikes.iterator();
        while (it.hasNext()) {
            LightningStrikeTelegraph strike = it.next();
            if (!strike.tick(level)) {
                it.remove();
            }
        }
    }

    // Inner Helper Class: Static Voltage Trail
    private static class StaticTrailInstance {
        final Vec3 pos;
        int ticksRemaining = 60; // 3 seconds duration

        StaticTrailInstance(Vec3 pos) {
            this.pos = pos;
        }

        boolean tick(ServerLevel level) {
            ticksRemaining--;
            if (ticksRemaining <= 0) return false;

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.1, pos.z, 2, 0.2, 0.05, 0.2, 0.01);

            if (ticksRemaining % 10 == 0) {
                AABB box = new AABB(pos.x - 1, pos.y - 0.5, pos.z - 1, pos.x + 1, pos.y + 1.5, pos.z + 1);
                List<Player> hitPlayers = level.getEntitiesOfClass(Player.class, box, Player::isAlive);
                for (Player p : hitPlayers) {
                    p.hurt(level.damageSources().lightningBolt(), p.getMaxHealth() * 0.01f + 0.5f);
                }
            }

            return true;
        }
    }

    // Inner Helper Class: Phase 3 Targeted Lightning Strike Telegraph
    private static class LightningStrikeTelegraph {
        final Vec3 pos;
        int ticksRemaining = 40; // 2 seconds telegraph

        LightningStrikeTelegraph(Vec3 pos) {
            this.pos = pos;
        }

        boolean tick(ServerLevel level) {
            ticksRemaining--;

            // Render telegraph circle warning (5.0 block radius, doubled from 2.5)
            ParticleUtils.renderCircle(level, ParticleTypes.SOUL_FIRE_FLAME, pos, 5.0, 24, 0.02);

            if (ticksRemaining <= 0) {
                // Strike lightning! Spawn cosmetic lightning bolt entity
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(pos.x, pos.y, pos.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }

                level.playSound(null, pos.x, pos.y, pos.z,
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0f, 1.0f);
                level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z, 8, 1.0, 1.0, 1.0, 0.05);

                List<Player> hitPlayers = HitboxUtils.getEntitiesInCircle(
                        level, Player.class, pos, 5.0, Player::isAlive);

                for (Player p : hitPlayers) {
                    p.hurt(level.damageSources().lightningBolt(), p.getMaxHealth() * 0.20f + 4.0f);
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, true)); // Stun (Slow V) for 2s
                }

                return false;
            }

            return true;
        }
    }
}
