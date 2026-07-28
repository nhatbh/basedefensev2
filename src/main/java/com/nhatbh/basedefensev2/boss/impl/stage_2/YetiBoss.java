package com.nhatbh.basedefensev2.boss.impl.stage_2;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.*;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class YetiBoss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("yeti_boss")
            .baseEntity("block_factorys_bosses:yeti")
            .baseStats(stats -> stats.health(2500f).speed(0.22f).damage(26f))
            .maxPoise(1500f)
            .baseScale(1.3f)
            .elements(ElementType.ICE)
            .poiseDamageReduction(0.92f)
            .addGlobalPassive(new SunforgedBulwarkPassive())

            // Phase 1: 100% - 50% HP
            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(600)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("glacial_prison")
                            .cooldown(750)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(GlacialPrisonSkill.create())
                            .build(), boss -> 120))

            // Phase 2: 50% - 0% HP
            .phase(2, phase -> phase
                    .hpThreshold(0.50f)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(540)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 70)
                    .addActive(ActiveSkill.builder("glacial_prison")
                            .cooldown(660)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(GlacialPrisonSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("storm_lance")
                            .cooldown(800)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(StormLanceSkill.create())
                            .build(), boss -> 140))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
