package com.nhatbh.basedefensev2.boss.impl.stage_3;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.ExplosiveDropkickSkill;
import com.nhatbh.basedefensev2.boss.impl.generic.FlamingDashSkill;
import com.nhatbh.basedefensev2.boss.impl.generic.SolarCataclysmSkill;
import com.nhatbh.basedefensev2.boss.impl.generic.SunforgedBulwarkPassive;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class WadjetMiniboss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("wadjet_miniboss")
            .baseEntity("cataclysm:wadjet")
            .baseStats(stats -> stats.health(2500f).speed(0.22f).damage(20f))
            .maxPoise(1500f)
            .baseScale(1.2f)
            .elements(ElementType.FIRE)
            .poiseDamageReduction(0.90f)
            .addGlobalPassive(new SunforgedBulwarkPassive())

            // Phase 1: 100% - 50% HP
            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(700)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(800)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 140))

            // Phase 2: 50% - 0% HP
            .phase(2, phase -> phase
                    .hpThreshold(0.50f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(640)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(720)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 120)
                    .addActive(ActiveSkill.builder("solar_cataclysm")
                            .cooldown(900)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(SolarCataclysmSkill.create())
                            .build(), boss -> 150))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
