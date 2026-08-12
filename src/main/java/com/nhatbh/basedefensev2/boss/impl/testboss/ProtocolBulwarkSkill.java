package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProtocolBulwarkSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("protocol_bulwark")
                .step("lock", 40)
                .onStart(ctx -> {
                    BossSkillHelper.broadcastMessage(ctx.boss(), "Activating defensive protocol.");
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 0.5f);
                    BossSkillHelper.stopMovement(ctx);
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    if (ctx.getTicks() % 4 == 0 && ctx.boss().level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ctx.boss().getX(), ctx.boss().getY() + 1.5,
                                ctx.boss().getZ(), 10, 0.5, 0.5, 0.5, 0.1);
                    }
                })
                .step("march", 100) // Longer duration but will likely explode due to distance
                .onStart(ctx -> {
                    BossSkillHelper.broadcastMessage(ctx.boss(), "None shall pass!");
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.ZOMBIFIED_PIGLIN_ANGRY, SoundSource.HOSTILE, 2.0f, 0.5f);

                    LivingEntity target = BossSkillHelper.getFurthestTarget(ctx, 100.0);
                    Vec3 forward = ctx.boss().getLookAngle().multiply(1, 0, 1).normalize();
                    if (target != null) {
                        forward = target.position().subtract(ctx.boss().position()).multiply(1, 0, 1).normalize();
                    }
                    Vec3 side = new Vec3(-forward.z, 0, forward.x);
                    ctx.data().put("march_dir", forward);

                    ctx.data().put("start_pos", ctx.boss().position());

                    List<UUID> guardIds = new ArrayList<>();
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        int count = 9;
                        double spacing = 0.8;
                        double startOffset = -(count - 1) * spacing / 2.0;

                        for (int i = 0; i < count; i++) {
                            Skeleton skeleton = EntityType.SKELETON.create(level);
                            if (skeleton != null) {
                                Vec3 spawnPos = ctx.boss().position().add(forward.scale(2))
                                        .add(side.scale(startOffset + i * spacing));
                                skeleton.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, ctx.boss().getYRot(), 0);
                                skeleton.setNoAi(true);
                                skeleton.setInvulnerable(true); // Don't let them be killed during the skill

                                // Equip shield and spear (trident or sword)
                                skeleton.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                                skeleton.setDropChance(EquipmentSlot.OFFHAND, 0);
                                skeleton.setDropChance(EquipmentSlot.MAINHAND, 0);

                                skeleton.addTag(com.nhatbh.basedefensev2.stage.ArenaConstants.ARENA_AFFILIATED_TAG);
                                level.addFreshEntity(skeleton);
                                guardIds.add(skeleton.getUUID());
                            }
                        }
                    }
                    ctx.data().put("guards", guardIds);
                    BossSkillHelper.stopMovement(ctx);
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 dir = (Vec3) ctx.data().get("march_dir");
                        Vec3 startPos = (Vec3) ctx.data().get("start_pos");
                        @SuppressWarnings("unchecked")
                        List<UUID> guardIds = (List<UUID>) ctx.data().get("guards");
                        double speed = 0.8;
                        double maxDist = 30.0;

                        boolean collisionDetected = false;
                        List<Skeleton> activeGuards = new ArrayList<>();

                        if (guardIds == null)
                            return;

                        for (UUID uuid : guardIds) {
                            if (level.getEntity(uuid) instanceof Skeleton skeleton) {
                                activeGuards.add(skeleton);
                                Vec3 nextPos = skeleton.position().add(dir.scale(speed));

                                // Check terrain collision or max range
                                if (!level.noCollision(skeleton, skeleton.getBoundingBox().move(dir.scale(speed)))
                                        || skeleton.position().distanceTo(startPos) > maxDist) {
                                    collisionDetected = true;
                                }

                                skeleton.setDeltaMovement(dir.scale(speed));
                                skeleton.moveTo(nextPos.x, nextPos.y, nextPos.z);

                                // Player interaction: Dragging
                                List<net.minecraft.world.entity.player.Player> targets = HitboxUtils
                                        .getEntitiesInCircle(level, net.minecraft.world.entity.player.Player.class,
                                                skeleton.position(), 1.5,
                                                e -> e.isAlive());

                                for (LivingEntity target : targets) {
                                    // Push player with the skeleton
                                    target.setDeltaMovement(dir.scale(speed).add(0, 0.05, 0));

                                    // Mixed damage per tick during drag
                                    float damage = BossSkillHelper.calculateMixedDamage(ctx, target, 5.0f, 10.0f);
                                    target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);

                                    // Blindness
                                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0, false, false));
                                }

                                // Visuals
                                level.sendParticles(ParticleTypes.CRIT, skeleton.getX(), skeleton.getY() + 1,
                                        skeleton.getZ(), 2, 0.2, 0.2, 0.2, 0.05);
                                if (ctx.getTicks() % 10 == 0) {
                                    level.playSound(null, skeleton.getX(), skeleton.getY(), skeleton.getZ(),
                                            SoundEvents.ARMOR_EQUIP_IRON, SoundSource.HOSTILE, 1.0f, 1.0f);
                                }
                            }
                        }

                        if (collisionDetected) {
                            // Explode and end skill
                            for (Skeleton skeleton : activeGuards) {
                                level.playSound(null, skeleton.getX(), skeleton.getY(), skeleton.getZ(),
                                        SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0f, 1.5f);
                                level.explode(ctx.boss(), skeleton.getX(), skeleton.getY(), skeleton.getZ(), 2.0f,
                                        false, Level.ExplosionInteraction.NONE);

                                // 30% Max HP Damage + 5s Root
                                List<net.minecraft.world.entity.player.Player> targets = HitboxUtils
                                        .getEntitiesInCircle(level, net.minecraft.world.entity.player.Player.class,
                                                skeleton.position(), 4.0,
                                                e -> e.isAlive());
                                for (LivingEntity target : targets) {
                                    float damage = BossSkillHelper.calculateMixedDamage(ctx, target, 5.0f, 10.0f);
                                    target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);
                                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 255,
                                            false, false)); // Root
                                }
                                skeleton.discard();
                            }
                            ctx.data().remove("guards");
                            ctx.stopSequence();
                            // Since we can't "end skill" easily from here without finishing steps, we'll
                            // just remove guards
                            // and let the remaining ticks run or jump to a dummy step if needed.
                        }
                    }
                })
                .build();
    }
}
