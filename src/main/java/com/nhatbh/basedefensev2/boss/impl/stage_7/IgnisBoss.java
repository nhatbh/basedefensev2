package com.nhatbh.basedefensev2.boss.impl.stage_7;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.*;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class IgnisBoss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("ignis_boss")
            .baseEntity("cataclysm:ignis")
            .baseStats(stats -> stats.health(500000f).speed(0.28f).damage(90f))
            .maxPoise(300000f)
            .baseScale(1.6f)
            .elements(ElementType.FIRE)
            .poiseDamageReduction(0.98f)
            .addGlobalPassive(new DragonsFuryPassive())

            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(360)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 50)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(480)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 80))

            .phase(2, phase -> phase
                    .hpThreshold(0.75f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(300)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 40)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(420)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 70)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(540)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 100))

            .phase(3, phase -> phase
                    .hpThreshold(0.50f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(240)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 30)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(360)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 60)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(480)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 90)
                    .addActive(ActiveSkill.builder("solar_cataclysm")
                            .cooldown(600)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(SolarCataclysmSkill.create())
                            .build(), boss -> 120))

            .phase(4, phase -> phase
                    .hpThreshold(0.25f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(180)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 20)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(300)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 50)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(420)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("solar_cataclysm")
                            .cooldown(540)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(SolarCataclysmSkill.create())
                            .build(), boss -> 110)
                    .addActive(ActiveSkill.builder("concentrated_laser")
                            .cooldown(660)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ConcentratedLaserSkill.create())
                            .build(), boss -> 140))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
