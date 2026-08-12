package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.impl.generic.active.*;
import com.nhatbh.basedefensev2.boss.impl.generic.passive.dragonsfury.DragonsFuryPassive;
import com.nhatbh.basedefensev2.boss.impl.generic.passive.overclock.OverclockPassive;
import com.nhatbh.basedefensev2.boss.impl.generic.passive.sunforged.SunforgedBulwarkPassive;
import com.nhatbh.basedefensev2.boss.impl.generic.passive.titansmantle.TitansMantlePassive;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.boss.skills.PassiveSkill;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class GenericSkillRegistry {

    public record SkillInfo(
            String id,
            ActiveSkill.Type type,
            int cooldown,
            boolean isTerrain,
            Supplier<com.nhatbh.basedefensev2.boss.skills.ActiveSequence> sequenceSupplier) {
    }

    private static final List<PassiveSkill> PASSIVES = new ArrayList<>();
    private static final List<SkillInfo> ACTIVES = new ArrayList<>();
    private static SkillInfo FLAMING_DASH;

    static {
        // Passives from com.nhatbh.basedefensev2.boss.impl.generic
        PASSIVES.add(new DragonsFuryPassive());
        PASSIVES.add(new OverclockPassive());
        PASSIVES.add(new SunforgedBulwarkPassive());
        PASSIVES.add(new TitansMantlePassive());

        // Basic & Tactical Actives (Minimum 400 ticks / 20s)
        FLAMING_DASH = new SkillInfo("flaming_dash", ActiveSkill.Type.BASIC, 400, false, FlamingDashSkill::create);
        ACTIVES.add(FLAMING_DASH);

        ACTIVES.add(new SkillInfo("concentrated_laser", ActiveSkill.Type.TACTICAL, 480, false,
                ConcentratedLaserSkill::create));
        ACTIVES.add(new SkillInfo("earthquake", ActiveSkill.Type.TACTICAL, 540, false, EarthquakeSkill::create));
        ACTIVES.add(new SkillInfo("explosive_dropkick", ActiveSkill.Type.BASIC, 425, false,
                ExplosiveDropkickSkill::create));
        ACTIVES.add(new SkillInfo("glacial_prison", ActiveSkill.Type.TACTICAL, 600, true, GlacialPrisonSkill::create));
        ACTIVES.add(new SkillInfo("lance_of_light", ActiveSkill.Type.TACTICAL, 550, false, LanceOfLightSkill::create));
        ACTIVES.add(
                new SkillInfo("solar_cataclysm", ActiveSkill.Type.TACTICAL, 700, false, SolarCataclysmSkill::create));
        ACTIVES.add(new SkillInfo("static_shock", ActiveSkill.Type.BASIC, 400, false, StaticShockSkill::create));
        ACTIVES.add(new SkillInfo("stone_spike", ActiveSkill.Type.TACTICAL, 500, true, StoneSpikeSkill::create));
        ACTIVES.add(new SkillInfo("storm_lance", ActiveSkill.Type.TACTICAL, 550, false, StormLanceSkill::create));
        ACTIVES.add(new SkillInfo("sword_barrage", ActiveSkill.Type.TACTICAL, 450, false, SwordBarrageSkill::create));
    }

    public static PassiveSkill getRandomPassive(Random random) {
        return PASSIVES.get(random.nextInt(PASSIVES.size()));
    }

    /**
     * Selects active skills with restrictions:
     * 1. 75% chance to include 'flaming_dash'.
     * 2. Maximum of ONE terrain skill (e.g. glacial_prison, stone_spike).
     */
    public static List<SkillInfo> getRandomActives(Random random, int count) {
        List<SkillInfo> result = new ArrayList<>();
        List<SkillInfo> pool = new ArrayList<>(ACTIVES);
        boolean hasTerrainSkill = false;

        // 1. High probability (75%) for Flaming Dash
        if (count > 0 && FLAMING_DASH != null && random.nextFloat() < 0.75f) {
            result.add(FLAMING_DASH);
            pool.remove(FLAMING_DASH);
        }

        // 2. Fill remaining slots respecting restrictions
        while (result.size() < count && !pool.isEmpty()) {
            int idx = random.nextInt(pool.size());
            SkillInfo candidate = pool.get(idx);

            if (candidate.isTerrain() && hasTerrainSkill) {
                // Skip if a terrain skill is already selected
                pool.remove(idx);
                continue;
            }

            if (candidate.isTerrain()) {
                hasTerrainSkill = true;
            }

            result.add(pool.remove(idx));
        }

        return result;
    }
}
