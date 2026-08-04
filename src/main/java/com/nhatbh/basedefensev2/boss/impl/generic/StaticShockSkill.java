package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;

public class StaticShockSkill {

    public static ActiveSequence create() {
        return ActiveSequence.builder("static_shock")
                // Phase 1: 1.0-Second Wind-Up Telegraph (20 Ticks)
                .step("wind_up", 20)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.8f, 1.8f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.TRIDENT_RIPTIDE_2, SoundSource.HOSTILE, 1.5f, 0.6f);

                    // Target random player within 40 blocks
                    List<Player> nearbyPlayers = HitboxUtils.getEntitiesInCircle(
                            ctx.boss().level(), Player.class, ctx.boss().position(), 40.0, BossSkillHelper::isValidTarget);

                    if (!nearbyPlayers.isEmpty()) {
                        Collections.shuffle(nearbyPlayers);
                        Player target = nearbyPlayers.get(0);

                        // Apply Static Shock effect for 6 seconds (120 ticks)
                        target.addEffect(new MobEffectInstance(ModEffects.STATIC_SHOCK.get(), 120, 0, false, true, true));
                    }
                })
                .onTick(ctx -> {
                    // Immobile while casting
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 origin = ctx.boss().getEyePosition();
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, origin.x, origin.y, origin.z, 8, 0.4, 0.4, 0.4, 0.1);
                        level.sendParticles(ParticleTypes.FLASH, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
                    }
                })
                .build();
    }
}
