package com.nhatbh.basedefensev2.boss.impl.stage_1;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.*;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class InfernalDragonBoss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("infernal_dragon_boss")
            .baseEntity("block_factorys_bosses:infernal_dragon")
            .baseStats(stats -> stats.health(1500f).speed(0.24f).damage(24f))
            .maxPoise(1000f)
            .baseScale(1.4f)
            .elements(ElementType.FIRE)
            .poiseDamageReduction(0.95f)
            .addGlobalPassive(new DragonsFuryPassive())

            // Phase 1: 100% - 75% HP (2 Actives, cooldowns > 30s / 600t)
            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(660) // 33s
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 90)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(720) // 36s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 120))

            // Phase 2: 75% - 50% HP (Unlocks 3rd Active: Earthquake)
            .phase(2, phase -> phase
                    .hpThreshold(0.75f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(640) // 32s
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 80)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(700) // 35s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 110)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(760) // 38s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 140))

            // Phase 3: 50% - 25% HP (Unlocks 4th Active: Solar Cataclysm)
            .phase(3, phase -> phase
                    .hpThreshold(0.50f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(620) // 31s
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 70)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(680) // 34s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(740) // 37s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 130)
                    .addActive(ActiveSkill.builder("solar_cataclysm")
                            .cooldown(900) // 45s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(SolarCataclysmSkill.create())
                            .build(), boss -> 150))

            // Phase 4: 25% - 0% HP (Unlocks 5th Active: Storm Lance)
            .phase(4, phase -> phase
                    .hpThreshold(0.25f)
                    .addActive(ActiveSkill.builder("flaming_dash")
                            .cooldown(600) // 30s
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(FlamingDashSkill.create())
                            .build(), boss -> 60)
                    .addActive(ActiveSkill.builder("explosive_dropkick")
                            .cooldown(660) // 33s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(ExplosiveDropkickSkill.create())
                            .build(), boss -> 90)
                    .addActive(ActiveSkill.builder("earthquake")
                            .cooldown(700) // 35s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(EarthquakeSkill.create())
                            .build(), boss -> 110)
                    .addActive(ActiveSkill.builder("solar_cataclysm")
                            .cooldown(840) // 42s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(SolarCataclysmSkill.create())
                            .build(), boss -> 140)
                    .addActive(ActiveSkill.builder("storm_lance")
                            .cooldown(800) // 40s
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(StormLanceSkill.create())
                            .build(), boss -> 160))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
