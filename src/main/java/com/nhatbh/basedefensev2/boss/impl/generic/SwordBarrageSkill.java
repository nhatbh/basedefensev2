package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.WeaponProjectile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

public class SwordBarrageSkill {

    public static ActiveSequence create() {
        Random random = new Random();

        return ActiveSequence.builder("sword_barrage")
                // Phase 1: 2-Second Wind-Up
                .step("wind_up", 40)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0f, 1.2f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 1.5f, 0.5f);
                })
                .onTick(ctx -> {
                    // Boss stays immobile horizontally
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        // Gathering energy particles
                        level.sendParticles(ParticleTypes.CRIT, ctx.boss().getX(), ctx.boss().getY() + 1.5,
                                ctx.boss().getZ(), 6, 0.8, 0.8, 0.8, 0.05);
                        level.sendParticles(ParticleTypes.ENCHANTED_HIT, ctx.boss().getX(), ctx.boss().getY() + 1.5,
                                ctx.boss().getZ(), 4, 0.5, 0.5, 0.5, 0.02);
                    }
                })

                // Phase 2: Firing Barrage of Swords (60 Ticks / 3.0 Seconds)
                .step("fire_barrage", 60)
                .onTick(ctx -> {
                    // Fire a sword every 4 ticks
                    if (ctx.getTicks() % 4 == 0 && ctx.boss().level() instanceof ServerLevel level) {
                        List<Player> nearbyPlayers = HitboxUtils.getEntitiesInCircle(
                                level, Player.class, ctx.boss().position(), 30.0, Player::isAlive);

                        net.minecraft.world.entity.Mob mob = ctx.boss() instanceof net.minecraft.world.entity.Mob m ? m
                                : null;
                        LivingEntity target = !nearbyPlayers.isEmpty()
                                ? nearbyPlayers.get(random.nextInt(nearbyPlayers.size()))
                                : (mob != null ? mob.getTarget() : null);

                        Vec3 origin = ctx.boss().position().add(
                                (random.nextDouble() - 0.5) * 1.5,
                                1.8 + random.nextDouble() * 0.5,
                                (random.nextDouble() - 0.5) * 1.5);

                        Vec3 targetPos = target != null ? target.position().add(0, 1.0, 0)
                                : origin.add(ctx.boss().getLookAngle().scale(10.0));
                        Vec3 dir = targetPos.subtract(origin).normalize();

                        // Add slight random spread
                        Vec3 velocity = dir.add(
                                (random.nextDouble() - 0.5) * 0.12,
                                (random.nextDouble() - 0.5) * 0.12,
                                (random.nextDouble() - 0.5) * 0.12).normalize().scale(1.4);

                        // Select random sword item
                        ItemStack swordItem = switch (random.nextInt(3)) {
                            case 0 -> new ItemStack(Items.DIAMOND_SWORD);
                            case 1 -> new ItemStack(Items.NETHERITE_SWORD);
                            default -> new ItemStack(Items.IRON_SWORD);
                        };

                        // Launch WeaponProjectile
                        WeaponProjectile.create(level, origin, velocity, ctx.boss(), swordItem)
                                .setSmall(true)
                                .setHitRadius(1.2f)
                                .setMaxLifetime(80)
                                .setTrailParticle(ParticleTypes.CRIT, 2)
                                .setOnHitEntity((proj, entity) -> {
                                    level.playSound(null, proj.getPosition().x, proj.getPosition().y,
                                            proj.getPosition().z,
                                            SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 1.5f, 1.2f);
                                    level.sendParticles(ParticleTypes.CRIT, proj.getPosition().x,
                                            proj.getPosition().y + 0.5,
                                            proj.getPosition().z, 15, 0.3, 0.3, 0.3, 0.1);

                                    if (entity instanceof LivingEntity targetLiving) {
                                        float damage = (targetLiving.getMaxHealth() * 0.075f) + 2.25f;
                                        targetLiving.hurt(level.damageSources().mobAttack(ctx.boss()), damage);
                                    }
                                })
                                .setOnHitBlock(proj -> {
                                    level.playSound(null, proj.getPosition().x, proj.getPosition().y,
                                            proj.getPosition().z,
                                            SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.6f, 1.8f);
                                    level.playSound(null, proj.getPosition().x, proj.getPosition().y,
                                            proj.getPosition().z,
                                            SoundEvents.TRIDENT_HIT_GROUND, SoundSource.HOSTILE, 1.2f, 0.8f);
                                    level.sendParticles(ParticleTypes.CRIT, proj.getPosition().x, proj.getPosition().y,
                                            proj.getPosition().z, 8, 0.2, 0.2, 0.2, 0.05);

                                    // Weapon stays stuck in the ground for 100 ticks (5 seconds)!
                                    proj.embedInGround(100);
                                })
                                .spawn();

                        level.playSound(null, origin.x, origin.y, origin.z,
                                SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1.5f, 0.8f + random.nextFloat() * 0.4f);
                    }
                })
                .build();
    }
}
