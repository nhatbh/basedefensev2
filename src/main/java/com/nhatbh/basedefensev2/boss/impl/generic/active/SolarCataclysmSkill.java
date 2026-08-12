package com.nhatbh.basedefensev2.boss.impl.generic.active;

import com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SolarCataclysmSkill {

    public static ActiveSequence create() {
        return ActiveSequence.builder("solar_cataclysm")
                // Initial 1 Second Wind-Up (20 Ticks)
                .step("wind_up", 20)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2.0f, 0.5f);
                })
                .onTick(ctx -> {
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.FLAME, ctx.boss().getX(), ctx.boss().getY() + 1.0,
                                ctx.boss().getZ(), 4, 0.3, 0.5, 0.3, 0.05);
                        level.sendParticles(ParticleTypes.SMOKE, ctx.boss().getX(), ctx.boss().getY() + 1.0,
                                ctx.boss().getZ(), 2, 0.2, 0.4, 0.2, 0.02);
                    }
                })

                // Phase 1: Small Ring Explosion (Red Counter Phase - No safe window)
                .step("phase1_small_ring", 20)
                .counter(ActiveSequence.CounterType.NORMAL, 20, 20)
                .punishment((ctx, event) -> {
                    // Red counter hit: player attacking boss during red phase triggers punishment
                    // (final explosion)!
                    ctx.jumpToStep("final_explosion");
                })
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0f, 0.8f);
                })
                .onTick(ctx -> {
                    // Boss is immobile during casting
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 center = ctx.boss().position().add(0, 0.1, 0);
                        ParticleUtils.renderCircle(level, ParticleTypes.FLAME, center, 4.0, 16, 0.0);
                    }

                    // Detonate AoE at tick 15
                    if (ctx.getTicks() == 15 && ctx.boss().level() instanceof ServerLevel level) {
                        level.playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 1.2f);
                        level.sendParticles(ParticleTypes.EXPLOSION, ctx.boss().getX(), ctx.boss().getY() + 0.5,
                                ctx.boss().getZ(), 5, 1.0, 0.2, 1.0, 0.1);

                        List<Player> hitPlayers = HitboxUtils.getEntitiesInCircle(
                                level, Player.class, ctx.boss().position(), 4.0, p -> p.isAlive());

                        for (Player p : hitPlayers) {
                            float damage = (p.getMaxHealth() * 0.075f) + 2.25f;
                            p.hurt(level.damageSources().mobAttack(ctx.boss()), damage);
                        }
                    }
                })

                // Phase 2: Bigger Ring Explosion (Red Counter Phase - No safe window)
                .step("phase2_big_ring", 25)
                .counter(ActiveSequence.CounterType.NORMAL, 25, 25)
                .punishment((ctx, event) -> {
                    // Red counter hit: player attacking boss during danger window triggers final
                    // explosion!
                    ctx.jumpToStep("final_explosion");
                })
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0f, 0.6f);
                })
                .onTick(ctx -> {
                    // Boss is immobile during casting
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 center = ctx.boss().position().add(0, 0.1, 0);
                        ParticleUtils.renderCircle(level, ParticleTypes.LAVA, center, 8.0, 24, 0.0);
                    }

                    // Detonate AoE at tick 20
                    if (ctx.getTicks() == 20 && ctx.boss().level() instanceof ServerLevel level) {
                        level.playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.8f, 1.0f);
                        level.sendParticles(ParticleTypes.EXPLOSION, ctx.boss().getX(), ctx.boss().getY() + 0.5,
                                ctx.boss().getZ(), 10, 2.0, 0.2, 2.0, 0.1);

                        List<Player> hitPlayers = HitboxUtils.getEntitiesInCircle(
                                level, Player.class, ctx.boss().position(), 8.0, p -> p.isAlive());

                        for (Player p : hitPlayers) {
                            float damage = (p.getMaxHealth() * 0.125f) + 3.75f;
                            p.hurt(level.damageSources().mobAttack(ctx.boss()), damage);
                        }
                    }
                })

                // Phase 3: 2-Second Counter Phase (1s Red Danger Window, 1s Green Counter
                // Window)
                .parryStep("phase3_counter_window", 40)
                .counter(ActiveSequence.CounterType.NORMAL, 20, 40)
                .punishment((ctx, event) -> {
                    // Striking during Red Window (ticks 0-20) triggers punishment (final
                    // explosion)!
                    ctx.jumpToStep("final_explosion");
                })
                .onCountered((ctx, event) -> {
                    // Striking during Green Window (ticks 20-40) depletes 30% current strength
                    // (scaled by SPECIAL_STRENGTH_DAMAGE_TAKEN_MULTIPLIER) & ends skill
                    float baseDepletion = ctx.getStrength() * 0.30f;
                    if (ctx.boss().getAttributes().hasAttribute(
                            com.nhatbh.basedefensev2.strength.ModAttributes.SPECIAL_STRENGTH_DAMAGE_TAKEN_MULTIPLIER
                                    .get())) {
                        baseDepletion *= (float) ctx.boss().getAttributeValue(
                                com.nhatbh.basedefensev2.strength.ModAttributes.SPECIAL_STRENGTH_DAMAGE_TAKEN_MULTIPLIER
                                        .get());
                    }
                    ctx.applyStrengthDamage(baseDepletion);
                    ctx.stopSequence();
                })
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.WITCH_DRINK, SoundSource.HOSTILE, 2.0f, 1.2f);
                })
                .onTick(ctx -> {
                    // Boss is immobile during counter window
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        if (ctx.getTicks() < 20) {
                            // Ticks 0-20: Red Phase visual warning (Danger window)
                            level.sendParticles(ParticleTypes.FLAME, ctx.boss().getX(), ctx.boss().getY() + 1.0,
                                    ctx.boss().getZ(), 4, 0.4, 0.6, 0.4, 0.05);
                            level.sendParticles(ParticleTypes.SMOKE, ctx.boss().getX(), ctx.boss().getY() + 1.0,
                                    ctx.boss().getZ(), 2, 0.2, 0.4, 0.2, 0.02);
                        } else {
                            // Ticks 20-40: Green Phase visual aura (Vulnerable counter window)
                            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ctx.boss().getX(),
                                    ctx.boss().getY() + 1.0,
                                    ctx.boss().getZ(), 8, 0.5, 0.8, 0.5, 0.05);
                        }
                    }
                })

                // Phase 4: Final 5-Block Wide Solar Cataclysm Explosion
                .step("final_explosion", 20)
                .onStart(ctx -> {
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        level.playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0f, 0.5f);
                        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ctx.boss().getX(), ctx.boss().getY() + 1.0,
                                ctx.boss().getZ(), 2, 0, 0, 0, 0);
                        level.sendParticles(ParticleTypes.FLASH, ctx.boss().getX(), ctx.boss().getY() + 1.0,
                                ctx.boss().getZ(), 2, 0, 0, 0, 0);

                        // 5 blocks wide explosion (5.0 radius)
                        List<Player> hitPlayers = HitboxUtils.getEntitiesInCircle(
                                level, Player.class, ctx.boss().position(), 5.0, p -> p.isAlive());

                        for (Player p : hitPlayers) {
                            // Clear beneficial effects before applying heavy final explosion damage
                            BossSkillHelper.clearBeneficialEffects(p);

                            // Deal half max HP (30%) + half flat (9.0) = 18.0 total damage on 30 HP player
                            float damage = (p.getMaxHealth() * 0.30f) + 9.0f;
                            p.hurt(level.damageSources().genericKill(), damage);

                            // Knock up high
                            Vec3 currentVel = p.getDeltaMovement();
                            p.setDeltaMovement(currentVel.x, 1.8, currentVel.z);
                            p.hurtMarked = true;
                        }
                    }
                })
                .onTick(ctx -> {
                    // Boss is immobile during impact recovery
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);
                })
                .build();
    }
}
