package com.nhatbh.basedefensev2.boss.impl.stage_3;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.*;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class HarbingerBoss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("harbinger_boss")
            .baseEntity("cataclysm:the_harbinger")
            .baseStats(stats -> stats.health(12000f).speed(0.25f).damage(32f))
            .maxPoise(8000f)
            .baseScale(1.3f)
            .elements(ElementType.LIGHTNING)
            .poiseDamageReduction(0.94f)
            .addGlobalPassive(new OverclockPassive())

            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("storm_lance")
                            .cooldown(540)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(StormLanceSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("static_shock")
                            .cooldown(600)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(StaticShockSkill.create())
                            .build(), boss -> 100))

            .phase(2, phase -> phase
                    .hpThreshold(0.66f)
                    .addActive(ActiveSkill.builder("storm_lance")
                            .cooldown(480)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(StormLanceSkill.create())
                            .build(), boss -> 70)
                    .addActive(ActiveSkill.builder("static_shock")
                            .cooldown(540)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(StaticShockSkill.create())
                            .build(), boss -> 90)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(720)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 120))

            .phase(3, phase -> phase
                    .hpThreshold(0.33f)
                    .addActive(ActiveSkill.builder("storm_lance")
                            .cooldown(420)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(StormLanceSkill.create())
                            .build(), boss -> 60)
                    .addActive(ActiveSkill.builder("static_shock")
                            .cooldown(480)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(StaticShockSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(660)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(EarthquakeSkill.create())
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
