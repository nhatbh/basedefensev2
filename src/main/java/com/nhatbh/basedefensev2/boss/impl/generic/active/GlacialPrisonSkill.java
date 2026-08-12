package com.nhatbh.basedefensev2.boss.impl.generic.active;

import com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.TemporaryBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class GlacialPrisonSkill {

    public static ActiveSequence create() {
        AtomicReference<List<Vec3>> targetPositionsRef = new AtomicReference<>(new ArrayList<>());

        return ActiveSequence.builder("glacial_prison")
                // Phase 1: 3-Second Wind-Up & Hover (Magic Counter Window)
                .parryStep("wind_up_hover", 60)
                .counter(ActiveSequence.CounterType.MAGIC, 0, 60)
                .onCountered((ctx, event) -> {
                    // Magic counter success: cancel sequence and drop boss back to ground
                    ctx.boss().removeEffect(MobEffects.LEVITATION);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 1.5f, 1.5f);
                    ctx.stopSequence();
                })
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.SNOW_GOLEM_SHOOT, SoundSource.HOSTILE, 2.0f, 0.5f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.5f, 1.2f);

                    // Select target positions matching player count (1 player -> 1 prison, 2
                    // players -> 2 prisons, max 3)
                    List<Player> nearbyPlayers = HitboxUtils.getEntitiesInCircle(
                            ctx.boss().level(), Player.class, ctx.boss().position(), 35.0, Player::isAlive);

                    List<Vec3> targets = new ArrayList<>();
                    if (!nearbyPlayers.isEmpty()) {
                        int count = Math.min(3, nearbyPlayers.size());
                        for (int i = 0; i < count; i++) {
                            targets.add(nearbyPlayers.get(i).position());
                        }
                    } else {
                        // Fallback around boss if no players
                        targets.add(ctx.boss().position());
                    }
                    targetPositionsRef.set(targets);
                })
                .onTick(ctx -> {
                    // Boss hovers up in the air using Levitation effect
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.LEVITATION, 10, 1, false, false));

                    // Immobile horizontally during casting
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        // Render snowflake circle telegraph indicator at target positions
                        List<Vec3> targets = targetPositionsRef.get();
                        for (Vec3 targetPos : targets) {
                            com.nhatbh.basedefensev2.boss.utils.ParticleUtils.renderCircle(
                                    level, ParticleTypes.SNOWFLAKE, targetPos, 2.5, 16, 0.02);
                        }

                        // Ice aura particles around hovering boss
                        level.sendParticles(ParticleTypes.SNOWFLAKE, ctx.boss().getX(), ctx.boss().getY() + 1.0,
                                ctx.boss().getZ(), 8, 0.8, 0.8, 0.8, 0.05);

                        // Magic counter cyan aura
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ctx.boss().getX(), ctx.boss().getY() + 0.5,
                                ctx.boss().getZ(), 4, 0.5, 0.5, 0.5, 0.02);
                    }
                })

                // Phase 2: Erupt Tall 8-Block Tapered Ice Spikes (15 Ticks)
                // Completing this step immediately frees the boss so it can move and fight
                // freely!
                .step("erupt_ice_prison", 15)
                .onStart(ctx -> {
                    // Remove levitation when erupting prisons
                    ctx.boss().removeEffect(MobEffects.LEVITATION);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        List<Vec3> targets = targetPositionsRef.get();

                        for (Vec3 targetPos : targets) {
                            int blockX = (int) Math.floor(targetPos.x);
                            int blockZ = (int) Math.floor(targetPos.z);
                            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);

                            Vec3 groundCenter = new Vec3(blockX + 0.5, groundY, blockZ + 0.5);

                            // Play eruption sound
                            level.playSound(null, groundCenter.x, groundCenter.y, groundCenter.z,
                                    SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.8f);
                            level.playSound(null, groundCenter.x, groundCenter.y, groundCenter.z,
                                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 1.8f);

                            // Particles on eruption
                            level.sendParticles(ParticleTypes.SNOWFLAKE, groundCenter.x, groundCenter.y + 1.0,
                                    groundCenter.z, 30, 1.5, 2.0, 1.5, 0.1);
                            level.sendParticles(ParticleTypes.EXPLOSION, groundCenter.x, groundCenter.y + 1.0,
                                    groundCenter.z, 3, 0.5, 0.5, 0.5, 0.05);

                            // Deal 30% HP damage to nearby players
                            List<Player> hitPlayers = HitboxUtils.getEntitiesInCircle(
                                    level, Player.class, groundCenter, 2.5, BossSkillHelper::canBeHitBySkill);

                            for (Player p : hitPlayers) {
                                float damage = (p.getMaxHealth() * 0.15f) + 4.5f;
                                p.hurt(level.damageSources().mobAttack(ctx.boss()), damage);

                                // Trapping status effects (10 seconds = 200 ticks)
                                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 3, false, true));
                                p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 3, false, true));
                            }

                            // Ensure boss is not trapped inside construct
                            ensureBossNotTrapped(ctx.boss(), groundCenter, level);

                            // Erupt Tall 8-Block Tapered Ice Spike Structure
                            // Height 0 & 1: 3x3 Outer Ring (Leaves center hollow for trapped player)
                            for (int dx = -1; dx <= 1; dx++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    if (dx == 0 && dz == 0)
                                        continue; // Hollow center cage
                                    for (int h = 0; h <= 1; h++) {
                                        BlockPos icePos = new BlockPos(blockX + dx, groundY + h, blockZ + dz);
                                        TemporaryBlockManager.placeTemporaryBlock(level, icePos,
                                                Blocks.ICE.defaultBlockState(), 200);
                                    }
                                }
                            }

                            // Height 2, 3 & 4: 5-Block Cross Shape (+)
                            for (int h = 2; h <= 4; h++) {
                                TemporaryBlockManager.placeTemporaryBlock(level,
                                        new BlockPos(blockX, groundY + h, blockZ), Blocks.ICE.defaultBlockState(), 200);
                                TemporaryBlockManager.placeTemporaryBlock(level,
                                        new BlockPos(blockX + 1, groundY + h, blockZ), Blocks.ICE.defaultBlockState(),
                                        200);
                                TemporaryBlockManager.placeTemporaryBlock(level,
                                        new BlockPos(blockX - 1, groundY + h, blockZ), Blocks.ICE.defaultBlockState(),
                                        200);
                                TemporaryBlockManager.placeTemporaryBlock(level,
                                        new BlockPos(blockX, groundY + h, blockZ + 1), Blocks.ICE.defaultBlockState(),
                                        200);
                                TemporaryBlockManager.placeTemporaryBlock(level,
                                        new BlockPos(blockX, groundY + h, blockZ - 1), Blocks.ICE.defaultBlockState(),
                                        200);
                            }

                            // Height 5 & 6: 1x1 Center Column Spire
                            for (int h = 5; h <= 6; h++) {
                                TemporaryBlockManager.placeTemporaryBlock(level,
                                        new BlockPos(blockX, groundY + h, blockZ), Blocks.ICE.defaultBlockState(), 200);
                            }

                            // Height 7: Single Tip Block using BLUE_ICE (Visual indicator showing where to
                            // break!)
                            // Runs independently of the boss sequence so the boss can move and attack
                            // freely!
                            TemporaryBlockManager.placeTemporaryBlock(level, new BlockPos(blockX, groundY + 7, blockZ),
                                    Blocks.BLUE_ICE.defaultBlockState(), 200, () -> {
                                        level.playSound(null, groundCenter.x, groundCenter.y, groundCenter.z,
                                                SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.5f, 0.5f);
                                        level.playSound(null, groundCenter.x, groundCenter.y, groundCenter.z,
                                                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 2.0f);

                                        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, groundCenter.x,
                                                groundCenter.y + 2.0,
                                                groundCenter.z, 40, 1.5, 2.0, 1.5, 0.2);
                                        level.sendParticles(ParticleTypes.EXPLOSION, groundCenter.x,
                                                groundCenter.y + 2.0,
                                                groundCenter.z, 5, 0.8, 1.0, 0.8, 0.05);

                                        List<Player> trappedPlayers = HitboxUtils.getEntitiesInCircle(
                                                level, Player.class, groundCenter, 2.5, BossSkillHelper::canBeHitBySkill);

                                        for (Player p : trappedPlayers) {
                                            com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.clearBeneficialEffects(p);
                                            float shatterDamage = (p.getMaxHealth() * 0.30f) + 9.0f;
                                            p.hurt(level.damageSources().freeze(), shatterDamage);
                                        }
                                    });
                        }
                    }
                })
                .onTick(ctx -> {
                    // Boss hovers down gently as skill finishes
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 1, false, false));
                })
                .build();
    }

    private static void ensureBossNotTrapped(net.minecraft.world.entity.LivingEntity boss, Vec3 constructCenter, ServerLevel level) {
        double dx = boss.getX() - constructCenter.x;
        double dz = boss.getZ() - constructCenter.z;
        double distSq = dx * dx + dz * dz;

        // If boss is within 3.5 blocks horizontally of construct center
        if (distSq < 12.25) {
            Vec3 horizDir;
            if (distSq < 0.01) {
                horizDir = new Vec3(1.0, 0, 0);
            } else {
                double dist = Math.sqrt(distSq);
                horizDir = new Vec3(dx / dist, 0, dz / dist);
            }

            double safeX = constructCenter.x + horizDir.x * 4.5;
            double safeZ = constructCenter.z + horizDir.z * 4.5;
            int safeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(safeX), (int) Math.floor(safeZ));

            boss.teleportTo(safeX, safeY, safeZ);
            boss.setDeltaMovement(horizDir.x * 0.6, 0.25, horizDir.z * 0.6);
            boss.hurtMarked = true;
            boss.hasImpulse = true;
        }
    }
}
