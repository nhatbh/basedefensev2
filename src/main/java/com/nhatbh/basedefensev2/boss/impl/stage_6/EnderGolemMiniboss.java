package com.nhatbh.basedefensev2.boss.impl.stage_6;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.*;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class EnderGolemMiniboss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("ender_golem_miniboss")
            .baseEntity("cataclysm:ender_golem")
            .baseStats(stats -> stats.health(50000f).speed(0.22f).damage(50f))
            .maxPoise(30000f)
            .baseScale(1.3f)
            .elements(ElementType.ENDER)
            .poiseDamageReduction(0.94f)
            .addGlobalPassive(new TitansMantlePassive())

            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(480)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 70)
                    .addActive(ActiveSkill.builder("stone_spike")
                            .cooldown(600)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(StoneSpikeSkill.create())
                            .build(), boss -> 100))

            .phase(2, phase -> phase
                    .hpThreshold(0.50f)
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
                            .build(), boss -> 120))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
