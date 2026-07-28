package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import com.nhatbh.basedefensev2.effects.PetrificationEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class DaybreakSentinelManager {

    private final LivingEntity boss;
    private final List<IronGolem> sentinels = new ArrayList<>();
    private final float maxSentinelHp;
    private int tickCounter = 0;
    private boolean spawned = false;

    public DaybreakSentinelManager(LivingEntity boss) {
        this.boss = boss;
        // Exactly 10% of boss max HP
        this.maxSentinelHp = Math.max(20.0f, boss.getMaxHealth() * 0.10f);
    }

    public void spawnSentinels() {
        if (spawned || !(boss.level() instanceof ServerLevel level))
            return;
        spawned = true;

        double baseRadius = 6.0;
        double[] angles = { 0.0, 180.0 }; // 2 Iron Golems

        for (double angleDeg : angles) {
            double rad = Math.toRadians(angleDeg);
            double x = boss.getX() + Math.cos(rad) * baseRadius;
            double z = boss.getZ() + Math.sin(rad) * baseRadius;
            double y = level
                    .getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            new BlockPos((int) Math.floor(x), 0, (int) Math.floor(z)))
                    .getY();

            IronGolem golem = EntityType.IRON_GOLEM.create(level);
            if (golem != null) {
                golem.moveTo(x, y, z, (float) angleDeg, 0);
                golem.setCustomName(net.minecraft.network.chat.Component.literal("§6Daybreak Sentinel"));
                golem.setCustomNameVisible(true);

                // Set health to 10% of boss max HP
                var maxHpAttr = golem.getAttribute(Attributes.MAX_HEALTH);
                if (maxHpAttr != null) {
                    maxHpAttr.setBaseValue(maxSentinelHp);
                }
                golem.setHealth(maxSentinelHp);
                golem.setPlayerCreated(false); // Hostile behavior
                golem.setGlowingTag(true);

                // Target nearest player
                List<Player> nearby = HitboxUtils.getEntitiesInCircle(level, Player.class, golem.position(), 40.0,
                        Player::isAlive);
                if (!nearby.isEmpty()) {
                    golem.setTarget(nearby.get(0));
                }

                level.addFreshEntity(golem);
                sentinels.add(golem);

                level.sendParticles(ParticleTypes.FLASH, x, y + 1.0, z, 2, 0.2, 0.2, 0.2, 0);
                level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5f, 1.5f);
            }
        }
    }

    public int tickAndGetRadianceBonus() {
        if (!spawned || sentinels.isEmpty() || !(boss.level() instanceof ServerLevel level))
            return 0;
        tickCounter++;

        int aliveCount = 0;

        for (int i = sentinels.size() - 1; i >= 0; i--) {
            IronGolem golem = sentinels.get(i);
            if (!golem.isAlive()) {
                // Death burst
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, golem.getX(), golem.getY() + 1.0, golem.getZ(), 30,
                        0.5, 0.5, 0.5, 0.2);
                level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.SHIELD_BREAK,
                        SoundSource.HOSTILE, 1.5f, 1.2f);
                sentinels.remove(i);
                continue;
            }

            aliveCount++;

            // Ensure hostile targeting towards players
            if (golem.getTarget() == null || !golem.getTarget().isAlive()) {
                List<Player> nearby = HitboxUtils.getEntitiesInCircle(level, Player.class, golem.position(), 40.0,
                        Player::isAlive);
                if (!nearby.isEmpty()) {
                    golem.setTarget(nearby.get(0));
                }
            }

            // Golden aura particles
            if (tickCounter % 5 == 0) {
                level.sendParticles(ParticleTypes.FLAME, golem.getX(), golem.getY() + 1.0, golem.getZ(), 2, 0.2, 0.4,
                        0.2, 0.02);
                level.sendParticles(ParticleTypes.END_ROD, golem.getX(), golem.getY() + 2.2, golem.getZ(), 1, 0.1, 0.1,
                        0.1, 0.01);
            }

            // Gleam pulse telegraph at 9s (180 ticks), pulse at 10s (200 ticks)
            int cycleTick = tickCounter % 200;
            if (cycleTick >= 180 && cycleTick < 200) {
                ParticleUtils.renderCircle(level, ParticleTypes.FLAME, golem.position(), 5.0, 24, 0.02);
            }

            if (cycleTick == 0) {
                // Execute 5-block Gleam pulse
                level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.HOSTILE, 1.0f, 1.8f);
                level.sendParticles(ParticleTypes.FLASH, golem.getX(), golem.getY() + 1.0, golem.getZ(), 1, 0, 0, 0, 0);

                List<Player> hitPlayers = HitboxUtils.getEntitiesInCircle(level, Player.class, golem.position(), 5.0,
                        Player::isAlive);
                for (Player p : hitPlayers) {
                    Vec3 pLook = p.getLookAngle().normalize();
                    Vec3 toSentinel = golem.getEyePosition().subtract(p.getEyePosition()).normalize();
                    if (pLook.dot(toSentinel) > 0.5) {
                        PetrificationEffect.addStage(p, 300, "Hit by Sentinel Gleam Pulse while Facing Sentinel");
                    }
                }
            }
        }

        // Each alive sentinel feeds +1 Radiance/s (tickCounter % 20 == 0)
        return (tickCounter % 20 == 0) ? aliveCount : 0;
    }

    public boolean isSentinel(LivingEntity entity) {
        return entity instanceof IronGolem golem && sentinels.contains(golem);
    }

    public boolean hasSpawned() {
        return spawned;
    }

    public void clear() {
        for (IronGolem golem : sentinels) {
            if (golem.isAlive())
                golem.discard();
        }
        sentinels.clear();
    }
}
