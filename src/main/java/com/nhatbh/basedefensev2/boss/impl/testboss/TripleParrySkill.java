package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;

import net.minecraft.world.entity.LivingEntity;

public class TripleParrySkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("triple_parry")
            // Stage 1
            .parryStep("p1", 10)
                .onStart(ctx -> ctx.log("Preparing attack 1... (Parry window open)"))
            .step("a1", 5)
                .onStart(ctx -> {
                    ctx.log("Attack 1!");
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
            // Stage 2
            .parryStep("p2", 10)
                .onStart(ctx -> ctx.log("Preparing attack 2... (Parry window open)"))
            .step("a2", 5)
                .onStart(ctx -> {
                    ctx.log("Attack 2!");
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
            // Stage 3
            .parryStep("p3", 10)
                .onStart(ctx -> ctx.log("Preparing attack 3... (Parry window open)"))
            .step("a3", 5)
                .onStart(ctx -> {
                    ctx.log("Attack 3!");
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
