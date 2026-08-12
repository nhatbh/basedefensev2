package com.nhatbh.basedefensev2.stage.generator;

import com.mojang.logging.LogUtils;
import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.generic.GenericSkillRegistry;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.classification.ClassificationManager;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.elemental.MobElementConfig;
import com.nhatbh.basedefensev2.registry.ModBosses;
import com.nhatbh.basedefensev2.stage.config.MobSpawnEntry;
import com.nhatbh.basedefensev2.stage.config.StageConfig;
import com.nhatbh.basedefensev2.stage.config.WaveConfig;
import com.nhatbh.basedefensev2.stage.config.WaveRewardConfig;
import org.slf4j.Logger;

import java.util.*;

public class RandomStageGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ElementType[] ALL_ELEMENTS = ElementType.values();

    /**
     * Calculates Boss Poise using the non-linear curve formula from PoiseAPI.
     */
    public static float calculatePoise(float maxHp) {
        return com.nhatbh.basedefensev2.api.PoiseAPI.calculateMobMaxPoise(maxHp);
    }

    /**
     * Re-registers all dynamic boss and miniboss definitions using the original
     * seed.
     * Called on world reload so that ModBosses lookups (gen_boss_stage_N) succeed.
     * Does NOT regenerate StageConfig data — stage configs are loaded from NBT.
     */
    public static void registerBossesOnly(long seed) {
        Random worldRandom = new Random(seed);
        Set<String> usedBosses = new HashSet<>();

        for (int stageNum = 1; stageNum <= 10; stageNum++) {
            // Mirror the exact same element selection used during generation
            ElementType elem1 = ALL_ELEMENTS[worldRandom.nextInt(ALL_ELEMENTS.length)];
            ElementType elem2 = ALL_ELEMENTS[worldRandom.nextInt(ALL_ELEMENTS.length)];
            while (elem2 == elem1 && ALL_ELEMENTS.length > 1) {
                elem2 = ALL_ELEMENTS[worldRandom.nextInt(ALL_ELEMENTS.length)];
            }

            Random rng = new Random(seed + stageNum * 31L);

            // HP values (must use same rng sequence as original generation)
            float bossHp = getBossHpForStage(stageNum, rng);
            float minibossHp = getMinibossHpForStage(stageNum, rng);

            Map<String, ClassificationManager.MobData> classifications = ClassificationManager.getClassifications();
            List<ElementType> stageElements = List.of(elem1, elem2);
            List<String> monsterList = filterMobsForElements(classifications, stageElements, "Monster");
            List<String> eliteList = filterMobsForElements(classifications, stageElements, "Elites");
            List<String> minibossList = filterMobsForElements(classifications, stageElements, "Miniboss");
            List<String> bossList = filterMobsForElements(classifications, stageElements, "Boss");

            if (monsterList.isEmpty())
                monsterList = filterMobsForElements(classifications, null, "Monster");
            if (eliteList.isEmpty())
                eliteList = filterMobsForElements(classifications, null, "Elites");
            if (eliteList.isEmpty())
                eliteList = monsterList;
            if (minibossList.isEmpty())
                minibossList = filterMobsForElements(classifications, null, "Miniboss");
            if (minibossList.isEmpty())
                minibossList = eliteList;
            if (bossList.isEmpty())
                bossList = filterMobsForElements(classifications, null, "Boss");
            if (bossList.isEmpty())
                bossList = minibossList;
            if (bossList.isEmpty())
                bossList = List.of("minecraft:zombie");

            List<String> availableBosses = new ArrayList<>(bossList);
            availableBosses.removeAll(usedBosses);
            if (availableBosses.isEmpty()) {
                availableBosses = filterMobsForElements(classifications, null, "Boss");
                availableBosses.removeAll(usedBosses);
            }
            if (availableBosses.isEmpty()) {
                availableBosses = filterMobsForElements(classifications, null, "Miniboss");
                availableBosses.removeAll(usedBosses);
            }
            if (availableBosses.isEmpty())
                availableBosses = bossList;

            String chosenBoss = availableBosses.get(rng.nextInt(availableBosses.size()));
            usedBosses.add(chosenBoss);

            // Re-register boss
            String bossId = "gen_boss_stage_" + stageNum;
            createAndRegisterDynamicBoss(bossId, chosenBoss, bossHp, stageNum, rng);

            // Re-register miniboss for stages 3+
            if (stageNum >= 3 && !minibossList.isEmpty()) {
                String mbId = "gen_miniboss_stage_" + stageNum;
                createAndRegisterDynamicMiniboss(mbId, minibossList.get(rng.nextInt(minibossList.size())), minibossHp,
                        stageNum, rng);
            }
        }

        LOGGER.info("[RandomStageGenerator] Re-registered dynamic bosses for all 10 stages.");
    }

    /**
     * Generates a complete 10-stage progression for a given world seed.
     */
    public static Map<String, StageConfig> generateWorldStages(long seed) {
        Map<String, StageConfig> stages = new LinkedHashMap<>();
        Random worldRandom = new Random(seed);

        // Track used boss IDs across stages to prevent overlap
        Set<String> usedBosses = new HashSet<>();

        for (int stageNum = 1; stageNum <= 10; stageNum++) {
            // Assign 2 distinct elements per stage
            ElementType elem1 = ALL_ELEMENTS[worldRandom.nextInt(ALL_ELEMENTS.length)];
            ElementType elem2 = ALL_ELEMENTS[worldRandom.nextInt(ALL_ELEMENTS.length)];
            while (elem2 == elem1 && ALL_ELEMENTS.length > 1) {
                elem2 = ALL_ELEMENTS[worldRandom.nextInt(ALL_ELEMENTS.length)];
            }
            List<ElementType> stageElements = List.of(elem1, elem2);

            StageConfig cfg = generateStage(seed, stageNum, stageElements, usedBosses);
            stages.put(cfg.id, cfg);
        }

        return stages;
    }

    private static StageConfig generateStage(long worldSeed, int stageNum, List<ElementType> elements,
            Set<String> usedBosses) {
        Random rng = new Random(worldSeed + stageNum * 31L);
        StageConfig cfg = new StageConfig();
        cfg.id = "stage_" + stageNum;
        cfg.order = stageNum - 1;
        cfg.trigger_seconds = (stageNum == 1) ? 3600 : 7200; // 1 hour for stage 1, 2 hours for stage 2+
        cfg.warmup_ticks = 1200; // 1 minute warmup
        cfg.spawn_radius = 25;
        cfg.scavenge_duration_ticks = 6000; // 5 mins scavenge after wave clear

        // Scaling Formulas (Stage 1 uses 1.0x baseline HP/Damage multipliers)
        double hpMultiplier = (stageNum == 1) ? 1.00 : Math.pow(1.65, stageNum - 1);
        double dmgMultiplier = (stageNum == 1) ? 1.00 : Math.pow(1.40, stageNum - 1);

        // Classifications for the 2 stage elements
        Map<String, ClassificationManager.MobData> classifications = ClassificationManager.getClassifications();
        List<String> monsterList = filterMobsForElements(classifications, elements, "Monster");
        List<String> eliteList = filterMobsForElements(classifications, elements, "Elites");
        List<String> minibossList = filterMobsForElements(classifications, elements, "Miniboss");
        List<String> bossList = filterMobsForElements(classifications, elements, "Boss");

        // Fallbacks if specific element pools are empty
        if (monsterList.isEmpty())
            monsterList = filterMobsForElements(classifications, null, "Monster");
        if (eliteList.isEmpty())
            eliteList = filterMobsForElements(classifications, null, "Elites");
        if (eliteList.isEmpty())
            eliteList = monsterList;

        if (minibossList.isEmpty())
            minibossList = filterMobsForElements(classifications, null, "Miniboss");
        if (minibossList.isEmpty())
            minibossList = eliteList;

        if (bossList.isEmpty())
            bossList = filterMobsForElements(classifications, null, "Boss");
        if (bossList.isEmpty())
            bossList = minibossList;
        if (bossList.isEmpty())
            bossList = List.of("minecraft:zombie");

        // Select non-overlapping Boss
        List<String> availableBosses = new ArrayList<>(bossList);
        availableBosses.removeAll(usedBosses);
        if (availableBosses.isEmpty()) {
            // If element boss pool exhausted, fall back to global Boss category
            availableBosses = filterMobsForElements(classifications, null, "Boss");
            availableBosses.removeAll(usedBosses);
        }
        if (availableBosses.isEmpty()) {
            // Further fallback to global Miniboss category
            availableBosses = filterMobsForElements(classifications, null, "Miniboss");
            availableBosses.removeAll(usedBosses);
        }
        if (availableBosses.isEmpty()) {
            // If all unique bosses exhausted, re-use from full boss list
            availableBosses = bossList;
        }

        String chosenBoss = availableBosses.get(rng.nextInt(availableBosses.size()));
        usedBosses.add(chosenBoss);

        cfg.waves = new ArrayList<>();

        // Boss & Miniboss HP Scaling (with +-10% seed-based randomization)
        float bossHp = getBossHpForStage(stageNum, rng);
        float minibossHp = getMinibossHpForStage(stageNum, rng);

        int stageBaseLevel = (stageNum == 1) ? 5 : (10 + (stageNum - 1) * 5);

        if (stageNum <= 2) {
            // Stage 1: 4 mob waves + 1 boss wave (5 waves total); Stage 2: 5 mob waves + 1
            // boss wave
            int mobWaveCount = (stageNum == 1) ? 4 : 5;
            for (int w = 1; w <= mobWaveCount; w++) {
                cfg.waves.add(generateMobWave("wave_" + w, w, stageNum, monsterList, eliteList, rng));
            }

            // Boss Wave (Dramatic cosmetic level: Stage 1 = Lv 25, Stage 2 = Lv 40)
            int bossWaveIdx = mobWaveCount + 1;
            int bossLevel = stageBaseLevel + 15 + (stageNum * 5);
            String bossId = "gen_boss_stage_" + stageNum;
            createAndRegisterDynamicBoss(bossId, chosenBoss, bossHp, stageNum, rng);
            cfg.waves.add(generateBossWave("wave_" + bossWaveIdx + "_boss", bossId, stageNum, bossLevel));

        } else {
            // Stages 3-10: Mob waves -> Miniboss wave -> Mob waves -> Boss wave
            int preMinibossWaves = (stageNum >= 5) ? 3 : 2;
            if (stageNum >= 7)
                preMinibossWaves = 4;

            int postMinibossWaves = (stageNum >= 4) ? 3 : 2;
            if (stageNum >= 6)
                postMinibossWaves = 4;
            if (stageNum >= 9)
                postMinibossWaves = 5;

            int waveIdx = 1;

            // Pre-Miniboss Mob Waves
            for (int i = 0; i < preMinibossWaves; i++) {
                cfg.waves.add(generateMobWave("wave_" + waveIdx, waveIdx, stageNum, monsterList, eliteList, rng));
                waveIdx++;
            }

            // Miniboss Wave (Dramatic cosmetic level: +10 above stage base)
            int mbLevel = stageBaseLevel + 10 + (stageNum * 2);
            String mbId = "gen_miniboss_stage_" + stageNum;
            createAndRegisterDynamicMiniboss(mbId, minibossList.get(rng.nextInt(minibossList.size())), minibossHp,
                    stageNum, rng);
            cfg.waves.add(generateBossWave("wave_" + waveIdx + "_miniboss", mbId, stageNum, mbLevel));
            waveIdx++;

            // Post-Miniboss Mob Waves
            for (int i = 0; i < postMinibossWaves; i++) {
                cfg.waves.add(generateMobWave("wave_" + waveIdx, waveIdx, stageNum, monsterList, eliteList, rng));
                waveIdx++;
            }

            // Boss Wave (Dramatic cosmetic level: e.g. Stage 5 = Lv 75, Stage 10 = Lv 150)
            int bossLevel = stageBaseLevel + 15 + (stageNum * 8);
            String bossId = "gen_boss_stage_" + stageNum;
            createAndRegisterDynamicBoss(bossId, chosenBoss, bossHp, stageNum, rng);
            cfg.waves.add(generateBossWave("wave_" + waveIdx + "_boss", bossId, stageNum, bossLevel));
        }

        return cfg;
    }

    private static List<String> filterMobsForElements(Map<String, ClassificationManager.MobData> dataMap,
            List<ElementType> elements, String category) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, ClassificationManager.MobData> entry : dataMap.entrySet()) {
            ClassificationManager.MobData data = entry.getValue();
            if (data.excluded)
                continue;
            if (!category.equalsIgnoreCase(data.category))
                continue;

            if (elements != null && !elements.isEmpty()) {
                ElementType mobElem = MobElementConfig.getElementFor(entry.getKey());
                if (mobElem == null || !elements.contains(mobElem))
                    continue;
            }

            result.add(entry.getKey());
        }
        return result;
    }

    private static WaveConfig generateMobWave(String id, int waveIdx, int stageNum, List<String> monsters,
            List<String> elites, Random rng) {
        WaveConfig wave = new WaveConfig();
        wave.id = id;
        wave.time_limit_ticks = 2400 + (waveIdx * 300);
        wave.arc_angle = 110 + Math.min(70, waveIdx * 10);
        wave.mobs = new ArrayList<>();

        int totalCount = (stageNum == 1) ? (4 + waveIdx * 2) : (8 + (stageNum * 3) + (waveIdx * 2));

        // Mob level scaling: Stage 1 = Lv. 5 base, Stage 2+ = Lv. 10 base, scaling +5
        // per stage and +0.5 per wave
        int stageBaseLevel = (stageNum == 1) ? 5 : (10 + (stageNum - 1) * 5);
        int mobLevel = stageBaseLevel + (waveIdx / 2);

        // Elite count: 15% rounded up (minimum 1 if elites available)
        int eliteTotalCount = !elites.isEmpty() ? Math.max(1, (int) Math.ceil(totalCount * 0.15)) : 0;
        int monsterTotalCount = totalCount - eliteTotalCount;

        // 1. Varied Regular Monster Entries (2 to 4 different monster types per wave)
        int monsterVariety = Math.min(monsters.size(), 2 + rng.nextInt(3));
        List<String> selectedMonsters = new ArrayList<>(monsters);
        Collections.shuffle(selectedMonsters, rng);

        int perMonsterCount = Math.max(1, monsterTotalCount / monsterVariety);

        for (int i = 0; i < monsterVariety; i++) {
            MobSpawnEntry monsterEntry = new MobSpawnEntry();
            monsterEntry.type = selectedMonsters.get(i);
            monsterEntry.count = perMonsterCount;
            monsterEntry.level = mobLevel;
            monsterEntry.formation = (i % 2 == 0) ? "arc" : "random";
            monsterEntry.distance_min = 6 + (i * 2);
            monsterEntry.distance_max = 14 + (i * 2);
            monsterEntry.hp_multiplier = 1.0;
            monsterEntry.damage_multiplier = 1.0;
            wave.mobs.add(monsterEntry);
        }

        // 2. Varied Elite Entries (1 to 2 different elite types per wave if elites
        // available)
        if (!elites.isEmpty() && eliteTotalCount > 0) {
            int eliteVariety = Math.min(elites.size(), 1 + rng.nextInt(2));
            List<String> selectedElites = new ArrayList<>(elites);
            Collections.shuffle(selectedElites, rng);

            int perEliteCount = Math.max(1, eliteTotalCount / eliteVariety);

            for (int i = 0; i < eliteVariety; i++) {
                MobSpawnEntry eliteEntry = new MobSpawnEntry();
                eliteEntry.type = selectedElites.get(i);
                eliteEntry.count = perEliteCount;
                eliteEntry.level = mobLevel; // Elites use same level as normal mobs
                eliteEntry.formation = "arc";
                eliteEntry.distance_min = 12;
                eliteEntry.distance_max = 20;
                eliteEntry.hp_multiplier = 1.0;
                eliteEntry.damage_multiplier = 1.0;
                wave.mobs.add(eliteEntry);
            }
        }

        return wave;
    }

    private static WaveConfig generateBossWave(String id, String bossId, int stageNum, int level) {
        WaveConfig wave = new WaveConfig();
        wave.id = id;
        wave.time_limit_ticks = 54000; // 45 minutes for boss
        wave.arc_angle = 360;
        wave.mobs = new ArrayList<>();

        MobSpawnEntry bossEntry = new MobSpawnEntry();
        bossEntry.is_boss = true;
        bossEntry.boss_id = bossId;
        bossEntry.level = level;
        wave.mobs.add(bossEntry);

        wave.rewards = new WaveRewardConfig();
        wave.rewards.xp = (int) (500 * Math.pow(stageNum, 1.4));
        wave.rewards.commands = List.of(
                "givecrate @a random all rare 1",
                "givecrate @a random all uncommon " + Math.min(5, stageNum));
        wave.rewards.items = new ArrayList<>();

        return wave;
    }

    private static float getBossHpForStage(int stage, Random rng) {
        float baseHp = switch (stage) {
            case 1 -> 600f;
            case 2 -> 3000f;
            case 3 -> 6000f;
            case 4 -> 12000f;
            case 5 -> 25000f;
            case 6 -> 50000f;
            case 7 -> 100000f;
            case 8 -> 180000f;
            case 9 -> 320000f;
            case 10 -> 500000f;
            default -> 1000f;
        };
        float multiplier = 0.90f + (rng.nextFloat() * 0.20f); // 0.90 to 1.10 (+-10%)
        return baseHp * multiplier;
    }

    private static float getMinibossHpForStage(int stage, Random rng) {
        float baseHp = switch (stage) {
            case 3 -> 1200f;
            case 4 -> 2500f;
            case 5 -> 5000f;
            case 6 -> 10000f;
            case 7 -> 20000f;
            case 8 -> 35000f;
            case 9 -> 65000f;
            case 10 -> 100000f;
            default -> 1000f;
        };
        float multiplier = 0.90f + (rng.nextFloat() * 0.20f); // 0.90 to 1.10 (+-10%)
        return baseHp * multiplier;
    }

    private static int getActiveSkillCountForStage(int stage) {
        if (stage <= 2)
            return 2;
        if (stage <= 4)
            return 3;
        if (stage <= 6)
            return 4;
        if (stage <= 8)
            return 5;
        return 6;
    }

    private static void createAndRegisterDynamicBoss(String bossId, String baseEntity, float hp, int stageNum,
            Random rng) {
        float poise = calculatePoise(hp);
        int skillCount = getActiveSkillCountForStage(stageNum);
        List<GenericSkillRegistry.SkillInfo> skills = GenericSkillRegistry.getRandomActives(rng, skillCount);

        BossDefinition.Builder builder = BossDefinition.builder(bossId)
                .baseEntity(baseEntity)
                .baseStats(stats -> stats.health(hp).speed(0.24f).damage(15f + stageNum * 5f))
                .maxPoise(poise)
                .baseScale(1.3f + (stageNum * 0.05f))
                .poiseDamageReduction(0.95f)
                .addGlobalPassive(GenericSkillRegistry.getRandomPassive(rng));

        // 4 Phases for Boss
        for (int p = 1; p <= 4; p++) {
            float hpThreshold = 1.0f - (p - 1) * 0.25f;
            final int phaseIndex = p;

            builder.phase(phaseIndex, phase -> {
                phase.hpThreshold(hpThreshold);
                // Assign skills progressively
                int skillsInThisPhase = Math.min(skills.size(), phaseIndex + 1);
                for (int s = 0; s < skillsInThisPhase; s++) {
                    GenericSkillRegistry.SkillInfo sk = skills.get(s);
                    phase.addActive(ActiveSkill.builder(sk.id() + "_p" + phaseIndex)
                            .cooldown(sk.cooldown())
                            .type(sk.type())
                            .sequence(sk.sequenceSupplier().get())
                            .build(), mob -> 100 + phaseIndex * 10);
                }
            });
        }

        ModBosses.register(builder.build());
    }

    private static void createAndRegisterDynamicMiniboss(String mbId, String baseEntity, float hp, int stageNum,
            Random rng) {
        float poise = calculatePoise(hp);
        int skillCount = Math.max(1, getActiveSkillCountForStage(stageNum) - 2);
        List<GenericSkillRegistry.SkillInfo> skills = GenericSkillRegistry.getRandomActives(rng, skillCount);

        BossDefinition.Builder builder = BossDefinition.builder(mbId)
                .baseEntity(baseEntity)
                .baseStats(stats -> stats.health(hp).speed(0.25f).damage(10f + stageNum * 3f))
                .maxPoise(poise)
                .baseScale(1.2f)
                .poiseDamageReduction(0.90f)
                .addGlobalPassive(GenericSkillRegistry.getRandomPassive(rng));

        // 2 Phases for Miniboss (100% and 50%)
        builder.phase(1, phase -> {
            phase.hpThreshold(1.0f);
            if (!skills.isEmpty()) {
                GenericSkillRegistry.SkillInfo sk = skills.get(0);
                phase.addActive(ActiveSkill.builder(sk.id() + "_mb1")
                        .cooldown(sk.cooldown())
                        .type(sk.type())
                        .sequence(sk.sequenceSupplier().get())
                        .build(), mob -> 100);
            }
        });

        builder.phase(2, phase -> {
            phase.hpThreshold(0.50f);
            for (GenericSkillRegistry.SkillInfo sk : skills) {
                phase.addActive(ActiveSkill.builder(sk.id() + "_mb2")
                        .cooldown(sk.cooldown())
                        .type(sk.type())
                        .sequence(sk.sequenceSupplier().get())
                        .build(), mob -> 110);
            }
        });

        ModBosses.register(builder.build());
    }

    /**
     * Generates a test StageConfig with a single Zombie Boss using Lightning Lance
     * on a 10s cooldown.
     */
    public static StageConfig generateTestZombieStage() {
        StageConfig cfg = new StageConfig();
        cfg.id = "test_zombie_stage";
        cfg.order = 0;
        cfg.trigger_seconds = 10;
        cfg.warmup_ticks = 100; // 5s warmup
        cfg.spawn_radius = 25;
        cfg.scavenge_duration_ticks = 1200;

        // Register test zombie boss definition with Lightning Lance
        String bossId = "test_zombie_boss";
        BossDefinition.Builder builder = BossDefinition.builder(bossId)
                .baseEntity("minecraft:zombie")
                .baseStats(stats -> stats.health(2000.0f).speed(0.25f).damage(10.0f))
                .maxPoise(300.0f)
                .addGlobalPassive(new com.nhatbh.basedefensev2.boss.impl.generic.passive.overclock.OverclockPassive());

        // Phase 1: 100% HP
        builder.phase(1, phase -> {
            phase.hpThreshold(1.0f);
            phase.addActive(ActiveSkill.builder("lightning_lance_test")
                    .cooldown(800) // 40 seconds
                    .type(ActiveSkill.Type.TACTICAL)
                    .sequence(com.nhatbh.basedefensev2.boss.impl.spells.LightningLanceSkill.create())
                    .build(), mob -> 100);
        });

        // Phase 2: 75% HP (Iron Gear)
        builder.phase(2, phase -> {
            phase.hpThreshold(0.75f);
            phase.armor("minecraft:iron_helmet", "minecraft:iron_chestplate",
                    "minecraft:iron_leggings", "minecraft:iron_boots");
            phase.mainhand("minecraft:iron_sword");
        });

        // Phase 3: 50% HP (Diamond Gear + Mount)
        builder.phase(3, phase -> {
            phase.hpThreshold(0.50f);
            phase.mount("minecraft:zombie_horse");
            phase.armor("minecraft:diamond_helmet", "minecraft:diamond_chestplate",
                    "minecraft:diamond_leggings", "minecraft:diamond_boots");
            phase.mainhand("minecraft:diamond_sword");
        });

        // Phase 4: 25% HP (Netherite Gear)
        builder.phase(4, phase -> {
            phase.hpThreshold(0.25f);
            phase.armor("minecraft:netherite_helmet", "minecraft:netherite_chestplate",
                    "minecraft:netherite_leggings", "minecraft:netherite_boots");
            phase.mainhand("minecraft:netherite_sword");
        });

        ModBosses.register(builder.build());

        WaveConfig wave = new WaveConfig();
        wave.id = "wave_1_test_boss";
        wave.time_limit_ticks = 6000;
        wave.arc_angle = 120;
        wave.mobs = new ArrayList<>();

        MobSpawnEntry bossEntry = new MobSpawnEntry();
        bossEntry.is_boss = true;
        bossEntry.boss_id = bossId;
        bossEntry.count = 1;
        bossEntry.formation = "random";
        wave.mobs.add(bossEntry);

        cfg.waves = List.of(wave);
        return cfg;
    }

    /**
     * Generates a test StageConfig where each wave spawns one registered
     * Boss/Miniboss from ModBosses.
     */
    public static StageConfig generateGauntletStage() {
        StageConfig cfg = new StageConfig();
        cfg.id = "gauntlet_stage";
        cfg.order = 0;
        cfg.trigger_seconds = 10;
        cfg.warmup_ticks = 100;
        cfg.spawn_radius = 25;
        cfg.scavenge_duration_ticks = 1200;
        cfg.waves = new ArrayList<>();

        Map<String, BossDefinition> bossMap = ModBosses.getAll();
        int waveIdx = 1;
        for (String bossId : bossMap.keySet()) {
            WaveConfig wave = new WaveConfig();
            wave.id = "wave_" + waveIdx + "_" + bossId;
            wave.time_limit_ticks = 54000;
            wave.arc_angle = 360;
            wave.mobs = new ArrayList<>();

            MobSpawnEntry bossEntry = new MobSpawnEntry();
            bossEntry.is_boss = true;
            bossEntry.boss_id = bossId;
            wave.mobs.add(bossEntry);

            cfg.waves.add(wave);
            waveIdx++;
        }

        if (cfg.waves.isEmpty()) {
            // Fallback if no bosses registered
            WaveConfig dummyWave = new WaveConfig();
            dummyWave.id = "wave_1_empty";
            dummyWave.time_limit_ticks = 1200;
            dummyWave.mobs = new ArrayList<>();
            cfg.waves.add(dummyWave);
        }

        return cfg;
    }
}
