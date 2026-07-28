package com.nhatbh.basedefensev2.boss.impl.stage_7;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.*;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class NetheriteMonstrosityMiniboss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("netherite_monstrosity_miniboss")
            .baseEntity("cataclysm:netherite_monstrosity")
            .baseStats(stats -> stats.health(100000f).speed(0.20f).damage(65f))
            .maxPoise(60000f)
            .baseScale(1.4f)
            .elements(ElementType.FIRE)
            .poiseDamageReduction(0.95f)
            .addGlobalPassive(new TitansMantlePassive())

            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(420)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 60)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(540)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 90))

            .phase(2, phase -> phase
                    .hpThreshold(0.50f)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(360)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 50)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(480)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(600)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 110))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
