package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import com.nhatbh.basedefensev2.boss.utils.TemporaryBlockManager;
import com.nhatbh.basedefensev2.effects.HeavyFootingEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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

public class StoneSpikeSkill {

    public static ActiveSequence create() {
        AtomicReference<List<Vec3>> targetPositionsRef = new AtomicReference<>(new ArrayList<>());

        return ActiveSequence.builder("stone_spike")
                // Phase 1: 2-Second Wind-Up (40 Ticks)
                .step("wind_up_earthquake", 40)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0f, 0.5f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.5f, 0.6f);

                    // Select target positions matching player count (up to 3)
                    List<Player> nearbyPlayers = HitboxUtils.getEntitiesInCircle(
                            ctx.boss().level(), Player.class, ctx.boss().position(), 35.0, Player::isAlive);

                    List<Vec3> targets = new ArrayList<>();
                    if (!nearbyPlayers.isEmpty()) {
                        int count = Math.min(3, nearbyPlayers.size());
                        for (int i = 0; i < count; i++) {
                            targets.add(nearbyPlayers.get(i).position());
                        }
                    } else {
                        // Fallback around boss
                        for (int i = 0; i < 3; i++) {
                            double angle = Math.toRadians((i * 120.0) + (ctx.boss().tickCount * 5));
                            double r = 5.0;
                            targets.add(ctx.boss().position().add(Math.cos(angle) * r, 0, Math.sin(angle) * r));
                        }
                    }
                    targetPositionsRef.set(targets);
                })
                .onTick(ctx -> {
                    // Immobile horizontally during casting
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        List<Vec3> targets = targetPositionsRef.get();
                        for (Vec3 targetPos : targets) {
                            ParticleUtils.renderCircle(level, ParticleTypes.FLAME, targetPos, 2.5, 16, 0.05);
                            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                                    targetPos.x, targetPos.y + 0.2, targetPos.z, 3, 0.5, 0.2, 0.5, 0.05);
                        }
                    }
                })

                // Phase 2: Erupt Massive Solid Block Stone Constructs (15 Ticks)
                .step("erupt_stone_spikes", 15)
                .onStart(ctx -> {
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        List<Vec3> targets = targetPositionsRef.get();
                        TitansMantleController controller = TitansMantleEventHandler.getController(ctx.boss());

                        for (Vec3 targetPos : targets) {
                            int blockX = (int) Math.floor(targetPos.x);
                            int blockZ = (int) Math.floor(targetPos.z);
                            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);

                            Vec3 groundCenter = new Vec3(blockX + 0.5, groundY, blockZ + 0.5);

                            // Eruption audio & visual effects
                            level.playSound(null, groundCenter.x, groundCenter.y, groundCenter.z,
                                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.6f);
                            level.playSound(null, groundCenter.x, groundCenter.y, groundCenter.z,
                                    SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.5f, 0.5f);

                            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                                    groundCenter.x, groundCenter.y + 1.0, groundCenter.z, 50, 1.5, 2.0, 1.5, 0.2);

                            // Deal damage & fling players on eruption
                            List<Player> hitPlayers = HitboxUtils.getEntitiesInCircle(level, Player.class, groundCenter, 3.0, Player::isAlive);
                            for (Player p : hitPlayers) {
                                float damage = (p.getMaxHealth() * 0.15f) + 5.0f;
                                p.hurt(level.damageSources().mobAttack(ctx.boss()), damage);
                                p.setDeltaMovement(p.getDeltaMovement().add(0, 0.75, 0));
                                p.hurtMarked = true;
                                HeavyFootingEffect.addStage(p, 200, "Hit by Stone Spike Eruption");
                            }

                            // Ensure boss is not trapped inside construct
                            ensureBossNotTrapped(ctx.boss(), groundCenter, level);

                            // Build Massive Solid Block Construct (Solid Tuff/Dripstone Blocks, No Tip)
                            List<BlockPos> spikeBlocks = new ArrayList<>();

                            // Layer 0 & 1: 3x3 Solid Block Foundation
                            for (int dx = -1; dx <= 1; dx++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    for (int h = 0; h <= 1; h++) {
                                        BlockPos bPos = new BlockPos(blockX + dx, groundY + h, blockZ + dz);
                                        TemporaryBlockManager.placeTemporaryBlock(level, bPos, Blocks.TUFF.defaultBlockState(), 1200);
                                        spikeBlocks.add(bPos);
                                    }
                                }
                            }

                            // Layer 2 & 3: 5-Block Cross Shape (+)
                            for (int h = 2; h <= 3; h++) {
                                BlockPos[] cross = new BlockPos[]{
                                        new BlockPos(blockX, groundY + h, blockZ),
                                        new BlockPos(blockX + 1, groundY + h, blockZ),
                                        new BlockPos(blockX - 1, groundY + h, blockZ),
                                        new BlockPos(blockX, groundY + h, blockZ + 1),
                                        new BlockPos(blockX, groundY + h, blockZ - 1)
                                };
                                for (BlockPos bPos : cross) {
                                    TemporaryBlockManager.placeTemporaryBlock(level, bPos, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 1200);
                                    spikeBlocks.add(bPos);
                                }
                            }

                            // Layer 4 & 5: 1x1 Solid Spire Column
                            for (int h = 4; h <= 5; h++) {
                                BlockPos bPos = new BlockPos(blockX, groundY + h, blockZ);
                                TemporaryBlockManager.placeTemporaryBlock(level, bPos, Blocks.TUFF.defaultBlockState(), 1200);
                                spikeBlocks.add(bPos);
                            }

                            // Register construct with controller's StoneSpikeManager for shockwave tracking & shattering
                            if (controller != null) {
                                StoneSpikeManager.SpikeInstance spike = new StoneSpikeManager.SpikeInstance();
                                spike.pos = groundCenter;
                                spike.blocks = spikeBlocks;
                                spike.erupted = true;
                                controller.getSpikeManager().addSpike(spike);
                            }
                        }
                    }
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
