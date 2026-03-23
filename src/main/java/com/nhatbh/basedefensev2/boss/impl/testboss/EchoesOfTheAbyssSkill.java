package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

public class EchoesOfTheAbyssSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("echoes_of_the_abyss")
            .step("abyssal_pull", 20)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0f, 0.5f);
                    BossSkillHelper.broadcastMessage(ctx.boss(), "The abyss calls for you!");
                })
                .onTick(ctx -> {
                    if (ctx.getTicks() % 2 == 0 && ctx.boss().level() instanceof ServerLevel level) {
                        level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, ctx.boss().getBoundingBox().inflate(15.0)).forEach(p -> {
                            Vec3 pullDir = ctx.boss().position().subtract(p.position()).normalize().scale(0.4);
                            p.setDeltaMovement(p.getDeltaMovement().add(pullDir));
                            // Optional: Periodic damage during pull
                            if (ctx.getTicks() % 10 == 0) {
                                float damage = BossSkillHelper.calculateMixedDamage(ctx, p, 10.0f, 20.0f);
                                p.hurt(ctx.boss().damageSources().indirectMagic(ctx.boss(), ctx.boss()), damage);
                            }
                        });
                        com.nhatbh.basedefensev2.boss.utils.ParticleUtils.renderCircle(level, ParticleTypes.REVERSE_PORTAL, ctx.boss().position(), 15.0, 20, 0.05);
                    }
                })
            .step("summon_clones", 10)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5f, 1.5f);
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        java.util.List<net.minecraft.world.entity.player.Player> players = level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, ctx.boss().getBoundingBox().inflate(100));
                        
                        for (int i = 0; i < 6; i++) {
                            Zombie clone = new Zombie(level);
                            clone.setPos(ctx.boss().getX() + (Math.random() - 0.5) * 8, ctx.boss().getY(), ctx.boss().getZ() + (Math.random() - 0.5) * 8);
                            
                            // Copy Equipment from Boss
                            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                                clone.setItemSlot(slot, ctx.boss().getItemBySlot(slot).copy());
                            }
                            clone.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0); // Don't drop boss weapon
                            
                            var speedAttr = clone.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                            if (speedAttr != null) speedAttr.setBaseValue(0.45f);
                            
                            if (!players.isEmpty()) {
                                clone.setTarget(players.get((int) (Math.random() * players.size())));
                            }
                            
                            clone.addTag(com.nhatbh.basedefensev2.stage.ArenaConstants.ARENA_AFFILIATED_TAG);
                            level.addFreshEntity(clone);
                            level.sendParticles(ParticleTypes.SOUL, clone.getX(), clone.getY() + 1, clone.getZ(), 10, 0.2, 0.5, 0.2, 0.1);
                        }
                    }
                })
            .build();
    }
}
