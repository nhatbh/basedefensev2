package com.nhatbh.basedefensev2.boss.impl.stage_6;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.*;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class EnderGuardianBoss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("ender_guardian_boss")
            .baseEntity("cataclysm:ender_guardian")
            .baseStats(stats -> stats.health(250000f).speed(0.26f).damage(70f))
            .maxPoise(150000f)
            .baseScale(1.5f)
            .elements(ElementType.ENDER)
            .poiseDamageReduction(0.97f)
            .addGlobalPassive(new OverclockPassive())

            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("sword_barrage")
                            .cooldown(420)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(SwordBarrageSkill.create())
                            .build(), boss -> 60)
                    .addActive(ActiveSkill.builder("concentrated_laser")
                            .cooldown(600)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ConcentratedLaserSkill.create())
                            .build(), boss -> 90))

            .phase(2, phase -> phase
                    .hpThreshold(0.66f)
                    .addActive(ActiveSkill.builder("sword_barrage")
                            .cooldown(360)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(SwordBarrageSkill.create())
                            .build(), boss -> 50)
                    .addActive(ActiveSkill.builder("concentrated_laser")
                            .cooldown(540)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ConcentratedLaserSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("storm_lance")
                            .cooldown(660)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(StormLanceSkill.create())
                            .build(), boss -> 110))

            .phase(3, phase -> phase
                    .hpThreshold(0.33f)
                    .addActive(ActiveSkill.builder("sword_barrage")
                            .cooldown(300)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(SwordBarrageSkill.create())
                            .build(), boss -> 40)
                    .addActive(ActiveSkill.builder("concentrated_laser")
                            .cooldown(480)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ConcentratedLaserSkill.create())
                            .build(), boss -> 70)
                    .addActive(ActiveSkill.builder("storm_lance")
                            .cooldown(600)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(StormLanceSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("solar_cataclysm")
                            .cooldown(750)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(SolarCataclysmSkill.create())
                            .build(), boss -> 130))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
