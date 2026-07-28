package com.nhatbh.basedefensev2.boss.impl.stage_5;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.*;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class CoralssusMiniboss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("coralssus_miniboss")
            .baseEntity("cataclysm:coralssus")
            .baseStats(stats -> stats.health(20000f).speed(0.20f).damage(40f))
            .maxPoise(12000f)
            .baseScale(1.3f)
            .elements(ElementType.AQUA)
            .poiseDamageReduction(0.93f)
            .addGlobalPassive(new TitansMantlePassive())

            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(540)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("glacial_prison")
                            .cooldown(720)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(GlacialPrisonSkill.create())
                            .build(), boss -> 110))

            .phase(2, phase -> phase
                    .hpThreshold(0.50f)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(480)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 70)
                    .addActive(ActiveSkill.builder("glacial_prison")
                            .cooldown(660)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(GlacialPrisonSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("stone_spike")
                            .cooldown(780)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(StoneSpikeSkill.create())
                            .build(), boss -> 130))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
