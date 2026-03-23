package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import net.minecraft.world.entity.Mob;

public class TestBoss {
    public static final BossDefinition INSTANCE = BossDefinition.builder("test_boss")
            .baseEntity("minecraft:zombie")
            .baseStats(stats -> stats.health(100000f).speed(0.35f).damage(10f))
            .maxPoise(100000f)
            .baseScale(1.5f)
            .elements(ElementType.ENDER)
            .poiseDamageReduction(0.97f)
            .phase(1, phase -> phase
                    .hpThreshold(1.0f)
                    .mount("minecraft:horse")
                    .armor("fantasy_armor:chess_board_knight_helmet", "fantasy_armor:chess_board_knight_chestplate",
                            "fantasy_armor:chess_board_knight_leggings", "fantasy_armor:chess_board_knight_boots")
                    .mainhand("simplyswords:wickpiercer", "{runic_power: 'no_socket', nether_power: 'no_socket'}")
                    .addActive(ActiveSkill.builder("sweeping_phalanx")
                            .cooldown(600)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(SweepingPhalanxSkill.create())
                            .build(), boss -> {
                                if (boss instanceof Mob mob) {
                                    var target = mob.getTarget();
                                    // Highly preferred at close range
                                    return (target != null && boss.distanceTo(target) <= 6) ? 50 : 25;
                                }
                                return 25;
                            })
                    .addActive(ActiveSkill.builder("trampling_charge")
                            .cooldown(600)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(TramplingChargeSkill.create())
                            .build(), boss -> {
                                if (boss instanceof Mob mob) {
                                    var target = mob.getTarget();
                                    // High priority when long range
                                    return (target != null && boss.distanceTo(target) > 12) ? 60 : 15;
                                }
                                return 15;
                            })
                    .addActive(ActiveSkill.builder("vanguard_advance")
                            .cooldown(600)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(VanguardAdvanceSkill.create())
                            .build(), boss -> {
                                if (boss instanceof Mob mob) {
                                    var target = mob.getTarget();
                                    // Preferred at mid-range
                                    return (target != null && boss.distanceTo(target) > 5
                                            && boss.distanceTo(target) <= 15) ? 40 : 20;
                                }
                                return 20;
                            })
                    .addActive(ActiveSkill.builder("graviton_singularity")
                            .cooldown(2400)
                            .type(ActiveSkill.Type.TACTICAL)
                            .startingCooldown(200) // 10 seconds
                            .sequence(GravitonSingularitySkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("protocol_bulwark")
                            .cooldown(2400)
                            .type(ActiveSkill.Type.TACTICAL)
                            .startingCooldown(300) // 15 seconds
                            .sequence(ProtocolBulwarkSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("aegis_reflection")
                            .cooldown(2400)
                            .type(ActiveSkill.Type.TACTICAL)
                            .startingCooldown(100) // 5 seconds
                            .sequence(AegisReflectionSkill.create())
                            .build(), boss -> 100))
            .phase(2, phase -> phase
                    .hpThreshold(0.75f)
                    .mount("minecraft:horse")
                    .armor("fantasy_armor:chess_board_knight_helmet", "fantasy_armor:chess_board_knight_chestplate",
                            "fantasy_armor:chess_board_knight_leggings", "fantasy_armor:chess_board_knight_boots")
                    .mainhand("cataclysm:cursed_bow")
                    .addPassive(new KitePassiveSkill(15f, 25f, 0.30f))
                    .addPassive(new ProjectileTrailPassiveSkill())
                    .addActive(ActiveSkill.builder("skirmisher_volley")
                            .cooldown(600)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(SkirmisherVolleySkill.create())
                            .build(), boss -> {
                                if (boss instanceof Mob mob) {
                                    var target = mob.getTarget();
                                    return (target != null && boss.distanceTo(target) < 20) ? 50 : 20;
                                }
                                return 20;
                            })
                    .addActive(ActiveSkill.builder("parting_shot")
                            .cooldown(600)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(PartingShotSkill.create())
                            .build(), boss -> {
                                if (boss instanceof Mob mob) {
                                    var target = mob.getTarget();
                                    return (target != null && boss.distanceTo(target) < 10) ? 80 : 10;
                                }
                                return 10;
                            })
                    .addActive(ActiveSkill.builder("rain_of_arrows")
                            .cooldown(600)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(RainOfArrowsSkill.create())
                            .build(), boss -> {
                                if (boss instanceof Mob mob) {
                                    var target = mob.getTarget();
                                    return (target != null && boss.distanceTo(target) > 15) ? 60 : 30;
                                }
                                return 30;
                            })
                    .addActive(ActiveSkill.builder("orbital_annihilation")
                            .cooldown(1200)
                            .startingCooldown(200)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(OrbitalAnnihilationSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("swarm_beacon")
                            .cooldown(1200)
                            .startingCooldown(400)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(SwarmBeaconSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("abyssal_piercer")
                            .cooldown(800)
                            .startingCooldown(100)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(AbyssalPiercerSkill.create())
                            .build(), boss -> 100))
            .phase(3, phase -> phase
                    .hpThreshold(0.5f)
                    .armor("fantasy_armor:chess_board_knight_helmet", "fantasy_armor:chess_board_knight_chestplate",
                            "fantasy_armor:chess_board_knight_leggings", "fantasy_armor:chess_board_knight_boots")
                    .mainhand("simplyswords:waxweaver", "{runic_power: 'socket_empty', nether_power: 'socket_empty'}")
                    .addActive(ActiveSkill.builder("abyssal_onslaught")
                            .cooldown(600)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(AbyssalOnslaughtSkill.create())
                            .build(), boss -> 40)
                    .addActive(ActiveSkill.builder("warp_flurry")
                            .cooldown(600)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(WarpFlurrySkill.create())
                            .build(), boss -> 60)
                    .addActive(ActiveSkill.builder("void_sunder")
                            .cooldown(600)
                            .type(ActiveSkill.Type.BASIC)
                            .sequence(VoidSunderSkill.create())
                            .build(), boss -> 30)
                    .addActive(ActiveSkill.builder("void_fissure")
                            .cooldown(1200)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(VoidFissureSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("echoes_of_the_abyss")
                            .cooldown(1200)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(EchoesOfTheAbyssSkill.create())
                            .build(), boss -> 100)
                    .addActive(ActiveSkill.builder("culling_blade")
                            .cooldown(1200)
                            .type(ActiveSkill.Type.TACTICAL)
                            .sequence(CullingBladeSkill.create())
                            .build(), boss -> 100))
            .phase(4, phase -> phase
                    .hpThreshold(0.25f)
                    .armor("fantasy_armor:chess_board_knight_helmet", "fantasy_armor:chess_board_knight_chestplate",
                            "fantasy_armor:chess_board_knight_leggings", "fantasy_armor:chess_board_knight_boots")
                    .mainhand("wom:antitheus")
                    // Phase 1 skills
                    .addActive(ActiveSkill.builder("sweeping_phalanx").cooldown(600)
                            .sequence(SweepingPhalanxSkill.create()).build(), boss -> 15)
                    .addActive(ActiveSkill.builder("trampling_charge").cooldown(600)
                            .sequence(TramplingChargeSkill.create()).build(), boss -> 15)
                    .addActive(ActiveSkill.builder("vanguard_advance").cooldown(600)
                            .sequence(VanguardAdvanceSkill.create()).build(), boss -> 15)
                    .addActive(ActiveSkill.builder("graviton_singularity").cooldown(2400)
                            .type(ActiveSkill.Type.TACTICAL).sequence(GravitonSingularitySkill.create()).build(),
                            boss -> 50)
                    .addActive(ActiveSkill.builder("protocol_bulwark").cooldown(2400).type(ActiveSkill.Type.TACTICAL)
                            .sequence(ProtocolBulwarkSkill.create()).build(), boss -> 50)
                    .addActive(ActiveSkill.builder("aegis_reflection").cooldown(2400).type(ActiveSkill.Type.TACTICAL)
                            .sequence(AegisReflectionSkill.create()).build(), boss -> 50)
                    // Phase 2 skills
                    .addActive(ActiveSkill.builder("skirmisher_volley").cooldown(600)
                            .sequence(SkirmisherVolleySkill.create()).build(), boss -> 15)
                    .addActive(ActiveSkill.builder("parting_shot").cooldown(600).sequence(PartingShotSkill.create())
                            .build(), boss -> 15)
                    .addActive(ActiveSkill.builder("rain_of_arrows").cooldown(600).sequence(RainOfArrowsSkill.create())
                            .build(), boss -> 20)
                    .addActive(ActiveSkill.builder("orbital_annihilation").cooldown(1200)
                            .type(ActiveSkill.Type.TACTICAL).sequence(OrbitalAnnihilationSkill.create()).build(),
                            boss -> 50)
                    .addActive(ActiveSkill.builder("swarm_beacon").cooldown(1200).type(ActiveSkill.Type.TACTICAL)
                            .sequence(SwarmBeaconSkill.create()).build(), boss -> 50)
                    .addActive(ActiveSkill.builder("abyssal_piercer").cooldown(800).type(ActiveSkill.Type.TACTICAL)
                            .sequence(AbyssalPiercerSkill.create()).build(), boss -> 50)
                    // Phase 3 skills
                    .addActive(ActiveSkill.builder("abyssal_onslaught").cooldown(600)
                            .sequence(AbyssalOnslaughtSkill.create()).build(), boss -> 15)
                    .addActive(
                            ActiveSkill.builder("warp_flurry").cooldown(600).sequence(WarpFlurrySkill.create()).build(),
                            boss -> 20)
                    .addActive(
                            ActiveSkill.builder("void_sunder").cooldown(600).sequence(VoidSunderSkill.create()).build(),
                            boss -> 15)
                    .addActive(ActiveSkill.builder("void_fissure").cooldown(1200).type(ActiveSkill.Type.TACTICAL)
                            .sequence(VoidFissureSkill.create()).build(), boss -> 50)
                    .addActive(ActiveSkill.builder("echoes_of_the_abyss").cooldown(1200).type(ActiveSkill.Type.TACTICAL)
                            .sequence(EchoesOfTheAbyssSkill.create()).build(), boss -> 50)
                    .addActive(ActiveSkill.builder("culling_blade").cooldown(1200).type(ActiveSkill.Type.TACTICAL)
                            .sequence(CullingBladeSkill.create()).build(), boss -> 50))
            .build();
}
