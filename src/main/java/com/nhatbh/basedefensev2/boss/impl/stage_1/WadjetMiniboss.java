package com.nhatbh.basedefensev2.boss.impl.stage_1;

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
            .baseStats(stats -> stats.health(1500f).speed(0.22f).damage(16f))
            .maxPoise(1000f)
            .baseScale(1.2f)
            .elements(ElementType.FIRE)
            .poiseDamageReduction(0.90f)
            .addGlobalPassive(new SunforgedBulwarkPassive())

            // Phase 1: 100% - 50% HP (2 Actives, cooldowns > 30s / 600t)
            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(700) // 35s
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(800) // 40s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 140))

            // Phase 2: 50% - 0% HP (Unlocks 3rd Active: Solar Cataclysm)
            .phase(2, phase -> phase
                    .hpThreshold(0.50f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(640) // 32s
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(720) // 36s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 120)
                    .addActive(ActiveSkill.builder("solar_cataclysm")
                            .cooldown(900) // 45s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(SolarCataclysmSkill.create())
                            .build(), boss -> 150))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
