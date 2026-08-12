package com.nhatbh.basedefensev2.boss.impl.generic.active;

import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.TemporaryBlockManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class StoneSpikeManager {

    private final LivingEntity boss;

    public static class SpikeInstance {
        public Vec3 pos;
        public List<BlockPos> blocks = new ArrayList<>();
        public int ageTicks = 0;
        public boolean erupted = true;
    }

    private final List<SpikeInstance> activeSpikes = new ArrayList<>();

    public StoneSpikeManager(LivingEntity boss) {
        this.boss = boss;
    }

    public void addSpike(SpikeInstance spike) {
        activeSpikes.add(spike);
    }

    public void tick() {
        if (!(boss.level() instanceof ServerLevel level))
            return;

        for (int i = activeSpikes.size() - 1; i >= 0; i--) {
            SpikeInstance spike = activeSpikes.get(i);
            spike.ageTicks++;

            // Persistent temporary block structure (remains for 60s / 1200 ticks)
            if (spike.ageTicks >= 1200) {
                shatterSpike(i, level);
            }
        }
    }

    public void checkAndShatterInRadius(Vec3 shockwaveCenter, double radius) {
        if (!(boss.level() instanceof ServerLevel level))
            return;

        for (int i = activeSpikes.size() - 1; i >= 0; i--) {
            SpikeInstance spike = activeSpikes.get(i);
            if (spike.erupted) {
                double dx = spike.pos.x - shockwaveCenter.x;
                double dz = spike.pos.z - shockwaveCenter.z;
                double dy = Math.abs(spike.pos.y - shockwaveCenter.y);

                // 2D distance <= radius + 2.0 (accounting for 3x3 block construct size) & Y diff <= 4.0
                if ((dx * dx + dz * dz) <= (radius + 2.0) * (radius + 2.0) && dy <= 4.0) {
                    shatterSpike(i, level);
                }
            }
        }
    }

    public void detonateAllSpikes() {
        if (!(boss.level() instanceof ServerLevel level))
            return;

        for (int i = activeSpikes.size() - 1; i >= 0; i--) {
            SpikeInstance spike = activeSpikes.get(i);
            if (spike.erupted) {
                shatterSpike(i, level);
            }
        }
    }

    private void shatterSpike(int index, ServerLevel level) {
        SpikeInstance spike = activeSpikes.remove(index);
        Vec3 pos = spike.pos;

        // Revert temporary physical blocks back to original state
        if (spike.blocks != null) {
            for (BlockPos bPos : spike.blocks) {
                TemporaryBlockManager.removeTemporaryBlock(level, bPos);
            }
        }

        // Explosion sound & particles on detonation
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.7f);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.5f, 0.5f);

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()), pos.x,
                pos.y + 1.0, pos.z, 80, 1.5, 2.0, 1.5, 0.25);
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z, 5, 0.8, 0.8, 0.8, 0.1);

        // 50% combined HP damage (25% max HP + 7.5 flat, calculated at 30 max health) in 6-block detonation radius
        List<Player> hitPlayers = HitboxUtils.getEntitiesInCircle(level, Player.class, pos, 6.0, Player::isAlive);
        for (Player p : hitPlayers) {
            float damage = (p.getMaxHealth() * 0.25f) + 7.5f;
            p.hurt(level.damageSources().mobAttack(boss), damage);
            p.setDeltaMovement(p.getDeltaMovement().add(0, 0.6, 0));
            p.hurtMarked = true;
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 200, 1, false, true));
        }
    }

    public void clear() {
        if (boss.level() instanceof ServerLevel level) {
            for (SpikeInstance spike : activeSpikes) {
                if (spike.blocks != null) {
                    for (BlockPos bPos : spike.blocks) {
                        TemporaryBlockManager.removeTemporaryBlock(level, bPos);
                    }
                }
            }
        }
        activeSpikes.clear();
    }
}
