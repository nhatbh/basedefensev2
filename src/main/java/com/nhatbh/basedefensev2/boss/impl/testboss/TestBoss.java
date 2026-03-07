package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import net.minecraft.world.entity.Mob;

public class TestBoss {
    public static final BossDefinition INSTANCE = BossDefinition.builder("test_boss")
            .baseEntity("minecraft:zombie")
            .baseStats(stats -> stats.health(200f).speed(0.35f).damage(10f))
            .maxPoise(100f)
            .poiseDamageReduction(0.95f)
            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .mount("minecraft:horse")
                    .mainhand("minecraft:iron_sword")
                    // .addActive(ActiveSkill.builder("dash")
                    //         .cooldown(1200)
                    //         .sequence(DashSkill.create())
                    //         .build(), boss -> {
                    //             if (boss instanceof Mob mob) {
                    //                 var target = mob.getTarget();
                    //                 return (target != null && boss.distanceTo(target) > 10) ? 20 : 0;
                    //             }
                    //             return 0;
                    //         })
                    .addActive(ActiveSkill.builder("sweeping_phalanx")
                            .cooldown(240)
                            .sequence(SweepingPhalanxSkill.create())
                            .build(), boss -> {
                                if (boss instanceof Mob mob) {
                                    var target = mob.getTarget();
                                    // Use when close range
                                    return (target != null && boss.distanceTo(target) <= 6) ? 50 : 0;
                                }
                                return 0;
                            })
                    .addActive(ActiveSkill.builder("trampling_charge")
                            .cooldown(240)
                            .sequence(TramplingChargeSkill.create())
                            .build(), boss -> {
                                if (boss instanceof Mob mob) {
                                    var target = mob.getTarget();
                                    // Use when long range
                                    return (target != null && boss.distanceTo(target) > 12) ? 60 : 0;
                                }
                                return 0;
                            })
                    .addActive(ActiveSkill.builder("vanguard_advance")
                            .cooldown(240)
                            .sequence(VanguardAdvanceSkill.create())
                            .build(), boss -> {
                                if (boss instanceof Mob mob) {
                                    var target = mob.getTarget();
                                    // Use when mid-range
                                    return (target != null && boss.distanceTo(target) > 5 && boss.distanceTo(target) <= 15) ? 40 : 0;
                                }
                                return 0;
                            })
                    )
            .build();
}
