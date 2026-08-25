package com.nhatbh.basedefensev2.boss.impl.generic.passive.overclock;

import com.nhatbh.basedefensev2.boss.client.BossResourceBarRegistry;
import com.nhatbh.basedefensev2.boss.skills.PassiveSkill;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import com.nhatbh.basedefensev2.utils.UUIDHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class OverclockPassive implements PassiveSkill {

    @Override
    public String getName() {
        return "Lôi Ảnh";
    }

    @Override
    public String getTitlePrefix() {
        return "Overclocked";
    }

    @Override
    public String getDescription() {
        return "Tích tụ động năng qua di chuyển và giao tranh. Khi đạt 100 nạp năng, tiến vào trạng thái Siêu Nạp (tăng 40% Tốc chạy, 30% Tốc đánh và chịu +30% sát thương Strength). Giai đoạn 2 (<25% HP) kích hoạt Giông Bão Cuồng Chấn, liên tục giội sét định vị buộc đối thủ phải luôn di chuyển.";
    }

    private static final Map<LivingEntity, OverclockPassive> ACTIVE_INSTANCES = new WeakHashMap<>();

    private float charge = 0.0f; // 0 to 100
    private Vec3 lastBossPos = null;
    private double accumulatedDistance = 0.0;

    private boolean isSupercharged = false;
    private int superchargedTimer = 0; // Ticks remaining in supercharged state (200 ticks = 10s)
    private boolean desperationActive = false;

    private int thunderstormTimer = 0; // Ticks counter for thunderstorm strikes in P2
    private int nextThunderstormDelay = 80; // Random delay between strikes (80 to 140 ticks = 4.0s to 7.0s)
    private final List<LightningStrikeTelegraph> activeStrikes = new ArrayList<>();

    private static final UUID CHARGE_SPEED_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("overclock", "charge_speed");
    private static final UUID CHARGE_ATTACK_SPEED_MOD_UUID = UUIDHelper.generateAttributeModifierUUID("overclock", "charge_attack_speed");

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
                () -> isSupercharged ? (desperationActive ? "OVERCLOCK [DESPERATION STORM]" : "OVERCLOCK [SUPERCHARGED]") : "Kinetic Charge",
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
        activeStrikes.clear();
    }

    @Override
    public void tick(LivingEntity boss) {
        if (boss.level().isClientSide() || !boss.isAlive() || com.nhatbh.basedefensev2.api.PoiseAPI.isExhausted(boss)) {
            removeModifiers(boss);
            return;
        }

        ServerLevel level = (ServerLevel) boss.level();

        // 1. Check Phase 2 (<= 25% HP Desperation)
        float hpPercent = boss.getHealth() / boss.getMaxHealth();
        if (!desperationActive && hpPercent <= 0.25f) {
            desperationActive = true;
            startSuperchargedState(boss);
        }

        if (desperationActive) {
            charge = 100.0f;
            isSupercharged = true;
        }

        // 2. Kinetic Charge Engine - Distance and Passive Combat Accumulation
        if (!isSupercharged) {
            if (lastBossPos != null) {
                double moved = boss.position().distanceTo(lastBossPos);
                accumulatedDistance += moved;
                if (accumulatedDistance >= 5.0) {
                    int points = (int) (accumulatedDistance / 5.0) * 5;
                    accumulatedDistance %= 5.0;
                    addCharge(boss, points);
                }
            }
            // Passive accumulation in combat (+3 charge / second = every 20 ticks)
            if (boss.level().getGameTime() % 20 == 0) {
                addCharge(boss, 3.0f);
            }
        }
        lastBossPos = boss.position();

        // 3. Update Stat Scaling Modifiers (+40% MS, +30% Atk Speed at 100 Charge)
        updateStatModifiers(boss);

        // 4. Tick Supercharged Duration (Phase 1)
        if (isSupercharged && !desperationActive) {
            superchargedTimer--;
            if (boss.level().getGameTime() % 5 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + 1.0, boss.getZ(), 4, 0.4, 0.6, 0.4, 0.05);
            }
            if (superchargedTimer <= 0) {
                endSuperchargedState(boss);
            }
        }

        // 5. Phase 2 Thunderstorm Assault (Spawns targeted strikes every 4-7s with RNG)
        if (desperationActive) {
            thunderstormTimer++;
            if (thunderstormTimer >= nextThunderstormDelay) {
                thunderstormTimer = 0;
                nextThunderstormDelay = 80 + level.random.nextInt(60); // 4.0s to 7.0s
                spawnThunderstormStrikes(level, boss);
            }
        }

        // 6. Tick Active Telegraph Strikes
        tickActiveStrikes(level);
    }

    public void addCharge(LivingEntity boss, float amount) {
        if (isSupercharged || desperationActive) return;

        charge = Math.min(100.0f, Math.max(0.0f, charge + amount));

        if (charge >= 100.0f) {
            startSuperchargedState(boss);
        }
    }

    public void onBossHit(LivingEntity attacker) {
        addCharge(attacker, 6.0f);
    }

    public boolean isSupercharged() {
        return isSupercharged;
    }

    public boolean isDesperationActive() {
        return desperationActive;
    }

    private void startSuperchargedState(LivingEntity boss) {
        isSupercharged = true;
        superchargedTimer = 200; // 10 seconds default in Phase 1

        if (boss.level() instanceof ServerLevel level) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0f, 1.2f);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1.0, boss.getZ(), 1, 0, 0, 0, 0);
        }
    }

    private void endSuperchargedState(LivingEntity boss) {
        if (desperationActive) return;
        isSupercharged = false;
        superchargedTimer = 0;
        charge = 0.0f;
    }

    private void updateStatModifiers(LivingEntity boss) {
        float ratio = charge / 100.0f;

        // Movement Speed Bonus: up to +40%
        var speedAttr = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(CHARGE_SPEED_MOD_UUID);
            if (ratio > 0) {
                speedAttr.addTransientModifier(new AttributeModifier(
                        CHARGE_SPEED_MOD_UUID, "OverclockSpeed", ratio * 0.40, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }

        // Attack Speed Bonus: up to +30%
        var atkSpeedAttr = boss.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpeedAttr != null) {
            atkSpeedAttr.removeModifier(CHARGE_ATTACK_SPEED_MOD_UUID);
            if (ratio > 0) {
                atkSpeedAttr.addTransientModifier(new AttributeModifier(
                        CHARGE_ATTACK_SPEED_MOD_UUID, "OverclockAttackSpeed", ratio * 0.30, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
    }

    private void removeModifiers(LivingEntity boss) {
        var speedAttr = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(CHARGE_SPEED_MOD_UUID);
        }
        var atkSpeedAttr = boss.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpeedAttr != null) {
            atkSpeedAttr.removeModifier(CHARGE_ATTACK_SPEED_MOD_UUID);
        }
    }

    private void spawnThunderstormStrikes(ServerLevel level, LivingEntity boss) {
        List<Player> players = level.getEntitiesOfClass(Player.class,
                boss.getBoundingBox().inflate(32), com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper::isValidTarget);

        if (players.isEmpty()) return;

        for (Player p : players) {
            activeStrikes.add(new LightningStrikeTelegraph(p.position()));
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

    // Inner Helper Class: Phase 2 Targeted Thunderstorm Lightning Strike Telegraph
    private static class LightningStrikeTelegraph {
        final Vec3 pos;
        int ticksRemaining = 24; // 1.2 seconds warning delay

        LightningStrikeTelegraph(Vec3 pos) {
            this.pos = pos;
        }

        boolean tick(ServerLevel level) {
            ticksRemaining--;

            // Render 1.5m circular telegraph warning ring
            ParticleUtils.renderCircle(level, ParticleTypes.SOUL_FIRE_FLAME, pos, 1.5, 16, 0.02);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.1, pos.z, 2, 0.3, 0.1, 0.3, 0.01);

            if (ticksRemaining <= 0) {
                // Strike lightning bolt
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(pos.x, pos.y, pos.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }

                level.playSound(null, pos.x, pos.y, pos.z,
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.5f, 1.2f);
                level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z, 4, 0.5, 0.5, 0.5, 0.05);

                List<Player> hitPlayers = HitboxUtils.getEntitiesInCircle(
                        level, Player.class, pos, 1.5, com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper::canBeHitBySkill);

                for (Player p : hitPlayers) {
                    p.hurt(level.damageSources().lightningBolt(), p.getMaxHealth() * 0.15f + 3.0f);
                }

                return false;
            }

            return true;
        }
    }
}
