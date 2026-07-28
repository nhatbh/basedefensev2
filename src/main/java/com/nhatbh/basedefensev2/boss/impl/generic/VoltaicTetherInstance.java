package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class VoltaicTetherInstance {

    private final Player player1;
    private final Player player2;
    private final LivingEntity boss;
    private int ticksRemaining = 120; // 6 seconds
    private final boolean isPhase4;

    public VoltaicTetherInstance(Player player1, Player player2, LivingEntity boss, boolean isPhase4) {
        this.player1 = player1;
        this.player2 = player2;
        this.boss = boss;
        this.isPhase4 = isPhase4;
    }

    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    /**
     * Ticks the tether. Returns false when tether has expired or snapped.
     */
    public boolean tick() {
        if (!player1.isAlive() || !player2.isAlive() || player1.level().isClientSide()) {
            return false;
        }

        ticksRemaining--;
        if (ticksRemaining <= 0) {
            // Safely discharged after 6s
            if (player1.level() instanceof ServerLevel level) {
                level.playSound(null, player1.getX(), player1.getY(), player1.getZ(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 2.0f);
            }
            return false;
        }

        if (!(player1.level() instanceof ServerLevel level)) {
            return true;
        }

        Vec3 pos1 = player1.position().add(0, 1.0, 0);
        Vec3 pos2 = player2.position().add(0, 1.0, 0);
        double dist = pos1.distanceTo(pos2);

        // Render electrical tether particle line between players
        ParticleUtils.renderLine(level, ParticleTypes.ELECTRIC_SPARK, pos1, pos2, 0.5, 0.02);
        if (level.random.nextInt(3) == 0) {
            ParticleUtils.renderLine(level, ParticleTypes.SOUL_FIRE_FLAME, pos1, pos2, 1.0, 0.01);
        }

        // Distance rules
        if (dist < 3.0) {
            // Too Close (< 3 blocks): Continuous zaps
            if (ticksRemaining % 5 == 0) {
                player1.hurt(level.damageSources().lightningBolt(), player1.getMaxHealth() * 0.02f + 1.0f);
                player2.hurt(level.damageSources().lightningBolt(), player2.getMaxHealth() * 0.02f + 1.0f);
                level.playSound(null, pos1.x, pos1.y, pos1.z, SoundEvents.BEE_STING, SoundSource.PLAYERS, 0.8f, 1.5f);
            }
        } else if (dist > 10.0) {
            // Too Far (> 10 blocks): Snap explosion!
            triggerSnap(level, pos1, pos2);
            return false;
        }

        return true;
    }

    private void triggerSnap(ServerLevel level, Vec3 pos1, Vec3 pos2) {
        level.playSound(null, pos1.x, pos1.y, pos1.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.5f, 1.2f);
        level.playSound(null, pos2.x, pos2.y, pos2.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.5f, 1.2f);

        level.sendParticles(ParticleTypes.EXPLOSION, pos1.x, pos1.y, pos1.z, 3, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.EXPLOSION, pos2.x, pos2.y, pos2.z, 3, 0.3, 0.3, 0.3, 0.05);

        float snapDamage1 = player1.getMaxHealth() * 0.25f + 5.0f;
        float snapDamage2 = player2.getMaxHealth() * 0.25f + 5.0f;

        player1.hurt(level.damageSources().lightningBolt(), snapDamage1);
        player2.hurt(level.damageSources().lightningBolt(), snapDamage2);

        // Stun in Phase 4 (or standard snap penalty)
        if (isPhase4) {
            player1.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4, false, true)); // Slow V for 3s
            player1.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, true)); // Blindness for 3s

            player2.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4, false, true));
            player2.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, true));
        }
    }
}
