package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;

import net.minecraft.world.entity.LivingEntity;

public class TripleParrySkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("triple_parry")
                .parryStep("p1", 60)
                .counter(ActiveSequence.CounterType.NORMAL, 30, 60)
                .punishment((ctx, event) -> {
                    if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
                        attacker.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 5.0f);
                    }
                })
                .step("a1", 5)
                .onStart(ctx -> {
                    LivingEntity target = null;
                    if (ctx.boss() instanceof net.minecraft.world.entity.Mob mob) {
                        target = mob.getTarget();
                    }
                    if (target != null) {
                        if (ctx.boss().distanceTo(target) < 3.5) {
                            target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 10f);
                        }
                    }
                })
                .parryStep("p2", 60)
                .counter(ActiveSequence.CounterType.NORMAL, 30, 60)
                .punishment((ctx, event) -> {
                    if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
                        attacker.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 7.0f);
                    }
                })
                .step("a2", 5)
                .onStart(ctx -> {
                    LivingEntity target = null;
                    if (ctx.boss() instanceof net.minecraft.world.entity.Mob mob) {
                        target = mob.getTarget();
                    }
                    if (target != null) {
                        if (ctx.boss().distanceTo(target) < 3.5) {
                            target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 10f);
                        }
                    }
                })
                .parryStep("p3", 60)
                .counter(ActiveSequence.CounterType.NORMAL, 30, 60)
                .punishment((ctx, event) -> {
                    if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
                        attacker.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 10.0f);
                    }
                })
                .step("a3", 5)
                .onStart(ctx -> {
                    LivingEntity target = null;
                    if (ctx.boss() instanceof net.minecraft.world.entity.Mob mob) {
                        target = mob.getTarget();
                    }
                    if (target != null) {
                        if (ctx.boss().distanceTo(target) < 3.5) {
                            target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 15f);
                        }
                    }
                })
                .build();
    }
}
