package com.nhatbh.basedefensev2.boss.impl.generic.active;

import com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import com.nhatbh.basedefensev2.boss.utils.ShockwaveEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class EarthquakeSkill {

    public static ActiveSequence create() {
        return ActiveSequence.builder("earthquake")
                // Phase 1: 1.5-Second Wind-Up / High Jump & Slam Telegraph
                .step("wind_up", 30)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0f, 0.6f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 0.5f);

                    // Boss leaps high into the air!
                    ctx.boss().setDeltaMovement(new net.minecraft.world.phys.Vec3(0, 1.2, 0));
                    ctx.boss().hurtMarked = true;
                })
                .onTick(ctx -> {
                    // Immobile horizontally while charging in mid-air
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        // Subtle ground charging particles
                        level.sendParticles(ParticleTypes.SMOKE, ctx.boss().getX(), ctx.boss().getY(),
                                ctx.boss().getZ(), 2, 0.8, 0.2, 0.8, 0.02);

                        // Expanding 60-block wide (30 block radius) telegraph indicator circle
                        double currentRadius = (ctx.getTicks() / 30.0) * 30.0;
                        ParticleUtils.renderCircle(level, ParticleTypes.CRIT, ctx.boss().position(), currentRadius, 28,
                                0.02);
                    }
                })

                // Phase 2: Ground Slam & 60-Block Wide Expanding Earthquake Ripple (15 Ticks)
                .step("ground_slam", 15)
                .onStart(ctx -> {
                    // Slam boss down to the ground
                    ctx.boss().setDeltaMovement(new net.minecraft.world.phys.Vec3(0, -2.5, 0));
                    ctx.boss().hurtMarked = true;

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        BlockPos bossPos = ctx.boss().blockPosition();

                        // Heavy slam sounds
                        level.playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.8f);
                        level.playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.8f, 0.5f);

                        // Slam explosion particles
                        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ctx.boss().getX(), ctx.boss().getY(),
                                ctx.boss().getZ(), 1, 0, 0, 0, 0);

                        // Trigger expanding 30-block radius (60-block wide) ripple shockwave with 50ms
                        // delay between rings
                        // Players who jump in the air will completely avoid the shockwave!
                        ShockwaveEffect.createRipple(level, bossPos, 30, 50L, ctx.boss());
                    }
                })
                .build();
    }
}
