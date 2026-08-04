package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;

public class ZombieTestBoss {

    public static final BossDefinition INSTANCE = BossDefinition.builder("zombie_test_boss")
            .baseEntity("minecraft:zombie")
            .baseStats(stats -> stats.health(2000f).speed(0.1f).damage(12f))
            .maxPoise(300f)
            .baseScale(1.5f)
            .elements(ElementType.LIGHTNING)
            .poiseDamageReduction(0.95f)
            // Global Passives & Actives across ALL phases
            .addGlobalPassive(new OverclockPassive())
            .addGlobalActive(ActiveSkill.builder("stone_spike")
                    .cooldown(800)
                    .type(ActiveSkill.Type.TACTICAL)
                    .sequence(StoneSpikeSkill.create())
                    .build(), boss -> 140)
            // .addGlobalActive(ActiveSkill.builder("flaming_dash")
            // .cooldown(300) // 15s base cooldown
            // .type(ActiveSkill.Type.BASIC)
            // .sequence(FlamingDashSkill.create())
            // .build(), boss -> 100)
            // .addGlobalActive(ActiveSkill.builder("solar_cataclysm")
            // .cooldown(400) // 20s base cooldown
            // .type(ActiveSkill.Type.TACTICAL)
            // .sequence(SolarCataclysmSkill.create())
            // .build(), boss -> 150)
            // .addGlobalActive(ActiveSkill.builder("glacial_prison")
            // .cooldown(360) // 18s base cooldown
            // .type(ActiveSkill.Type.TACTICAL)
            // .sequence(GlacialPrisonSkill.create())
            // .build(), boss -> 140)
            // .addGlobalActive(ActiveSkill.builder("sword_barrage")
            // .cooldown(280) // 14s base cooldown
            // .type(ActiveSkill.Type.BASIC)
            // .sequence(SwordBarrageSkill.create())
            // .build(), boss -> 120)
            // .addGlobalActive(ActiveSkill.builder("earthquake")
            // .cooldown(320) // 16s base cooldown
            // .type(ActiveSkill.Type.TACTICAL)
            // .sequence(EarthquakeSkill.create())
            // .build(), boss -> 130)
            // .addGlobalActive(ActiveSkill.builder("explosive_dropkick")
            // .cooldown(340) // 17s base cooldown
            // .type(ActiveSkill.Type.TACTICAL)
            // .sequence(ExplosiveDropkickSkill.create())
            // .build(), boss -> 150)
            // .addGlobalActive(ActiveSkill.builder("concentrated_laser")
            // .cooldown(400) // 20s base cooldown
            // .type(ActiveSkill.Type.TACTICAL)
            // .sequence(ConcentratedLaserSkill.create())
            // .build(), boss -> 160)
            // .addGlobalActive(ActiveSkill.builder("lance_of_light")
            // .cooldown(360) // 18s base cooldown
            // .type(ActiveSkill.Type.TACTICAL)
            // .sequence(LanceOfLightSkill.create())
            // .build(), boss -> 170)
            // .addGlobalActive(ActiveSkill.builder("static_shock")
            // .cooldown(380) // 19s base cooldown
            // .type(ActiveSkill.Type.TACTICAL)
            // .sequence(StaticShockSkill.create())
            // .build(), boss -> 180)
            // .addGlobalActive(ActiveSkill.builder("storm_lance")
            // .cooldown(340) // 17s base cooldown
            // .type(ActiveSkill.Type.TACTICAL)
            // .sequence(StormLanceSkill.create())
            // .build(), boss -> 190)
            // Phase 1: 100% - 75% HP
            .phase(1, phase -> phase
                    .hpThreshold(1.0f))
            // Phase 2: 75% - 50% HP (Iron Gear)
            .phase(2, phase -> phase
                    .hpThreshold(0.75f)
                    .armor("minecraft:iron_helmet", "minecraft:iron_chestplate",
                            "minecraft:iron_leggings", "minecraft:iron_boots")
                    .mainhand("minecraft:iron_sword"))
            // Phase 3: 50% - 25% HP (Diamond Gear + Mount)
            .phase(3, phase -> phase
                    .hpThreshold(0.50f)
                    .mount("minecraft:zombie_horse")
                    .armor("minecraft:diamond_helmet", "minecraft:diamond_chestplate",
                            "minecraft:diamond_leggings", "minecraft:diamond_boots")
                    .mainhand("minecraft:diamond_sword"))
            // Phase 4: 25% - 0% HP (Netherite Gear)
            .phase(4, phase -> phase
                    .hpThreshold(0.25f)
                    .armor("minecraft:netherite_helmet", "minecraft:netherite_chestplate",
                            "minecraft:netherite_leggings", "minecraft:netherite_boots")
                    .mainhand("minecraft:netherite_sword"))
            .build();

    public static void register() {
        ModBosses.register(INSTANCE);
    }
}
