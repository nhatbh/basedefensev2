package com.nhatbh.basedefensev2.boss.impl.stage_4;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.*;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class AncientRemnantBoss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("ancient_remnant_boss")
            .baseEntity("cataclysm:ancient_remnant")
            .baseStats(stats -> stats.health(40000f).speed(0.24f).damage(45f))
            .maxPoise(25000f)
            .baseScale(1.4f)
            .elements(ElementType.EVOCATION)
            .poiseDamageReduction(0.95f)
            .addGlobalPassive(new TitansMantlePassive())

            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(540)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("stone_spike")
                            .cooldown(660)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(StoneSpikeSkill.create())
                            .build(), boss -> 110))

            .phase(2, phase -> phase
                    .hpThreshold(0.66f)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(480)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 70)
                    .addActive(ActiveSkill.builder("stone_spike")
                            .cooldown(600)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(StoneSpikeSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(720)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 120))

            .phase(3, phase -> phase
                    .hpThreshold(0.33f)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(420)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 60)
                    .addActive(ActiveSkill.builder("stone_spike")
                            .cooldown(540)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(StoneSpikeSkill.create())
                            .build(), boss -> 90)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(660)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 110)
                    .addActive(ActiveSkill.builder("solar_cataclysm")
                            .cooldown(840)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(SolarCataclysmSkill.create())
                            .build(), boss -> 140))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
