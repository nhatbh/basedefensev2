package com.nhatbh.basedefensev2.boss.impl.stage_5;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.*;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class LeviathanBoss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("leviathan_boss")
            .baseEntity("cataclysm:the_leviathan")
            .baseStats(stats -> stats.health(100000f).speed(0.25f).damage(55f))
            .maxPoise(60000f)
            .baseScale(1.5f)
            .elements(ElementType.AQUA)
            .poiseDamageReduction(0.96f)
            .addGlobalPassive(new SunforgedBulwarkPassive())

            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("storm_lance")
                            .cooldown(480)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(StormLanceSkill.create())
                            .build(), boss -> 70)
                    .addActive(ActiveSkill.builder("glacial_prison")
                            .cooldown(660)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(GlacialPrisonSkill.create())
                            .build(), boss -> 100))

            .phase(2, phase -> phase
                    .hpThreshold(0.66f)
                    .addActive(ActiveSkill.builder("storm_lance")
                            .cooldown(420)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(StormLanceSkill.create())
                            .build(), boss -> 60)
                    .addActive(ActiveSkill.builder("glacial_prison")
                            .cooldown(600)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(GlacialPrisonSkill.create())
                            .build(), boss -> 90)
                    .addActive(ActiveSkill.builder("concentrated_laser")
                            .cooldown(720)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ConcentratedLaserSkill.create())
                            .build(), boss -> 120))

            .phase(3, phase -> phase
                    .hpThreshold(0.33f)
                    .addActive(ActiveSkill.builder("storm_lance")
                            .cooldown(360)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(StormLanceSkill.create())
                            .build(), boss -> 50)
                    .addActive(ActiveSkill.builder("glacial_prison")
                            .cooldown(540)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(GlacialPrisonSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("concentrated_laser")
                            .cooldown(660)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ConcentratedLaserSkill.create())
                            .build(), boss -> 110)
                    .addActive(ActiveSkill.builder("solar_cataclysm")
                            .cooldown(800)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(SolarCataclysmSkill.create())
                            .build(), boss -> 140))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
