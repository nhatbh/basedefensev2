package com.nhatbh.basedefensev2.boss.impl.stage_4;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.*;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class ProwlerMiniboss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("prowler_miniboss")
            .baseEntity("cataclysm:the_prowler")
            .baseStats(stats -> stats.health(8000f).speed(0.24f).damage(35f))
            .maxPoise(5000f)
            .baseScale(1.2f)
            .elements(ElementType.EVOCATION)
            .poiseDamageReduction(0.92f)
            .addGlobalPassive(new OverclockPassive())

            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(600)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(720)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 110))

            .phase(2, phase -> phase
                    .hpThreshold(0.50f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(540)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 70)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(660)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(780)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 130))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
