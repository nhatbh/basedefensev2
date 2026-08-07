package com.nhatbh.basedefensev2.level;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class MobLevelConfig {

    // [Mobs]
    public static final Set<String> BLACKLIST = new HashSet<>();
    public static final Set<String> WHITELIST = new HashSet<>();
    public static final Set<String> HIDDEN_LEVEL_BLACKLIST = new HashSet<>();

    public static boolean ALWAYS_SHOW_LEVELS = false;
    public static boolean ONLY_SHOW_LEVELS_ON_LOOK = true;
    public static float BONUS_XP_PER_LEVEL = 0.1f;

    // World Level Overworld Scaling Arrays (Index = World Level 0..10)
    public static final int[] OVERWORLD_BASE_LEVELS = new int[]{1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50};
    public static final int[] MOB_LEVEL_CAPS = new int[]{15, 30, 50, 70, 95, 125, 160, 200, 245, 295, 350};

    public static int getOverworldBaseLevel(int worldLevel) {
        if (worldLevel < 0) return OVERWORLD_BASE_LEVELS[0];
        if (worldLevel >= OVERWORLD_BASE_LEVELS.length) {
            return OVERWORLD_BASE_LEVELS[OVERWORLD_BASE_LEVELS.length - 1] + (worldLevel - OVERWORLD_BASE_LEVELS.length + 1) * 5;
        }
        return OVERWORLD_BASE_LEVELS[worldLevel];
    }

    public static int getMobLevelCap(int worldLevel) {
        if (worldLevel < 0) return MOB_LEVEL_CAPS[0];
        if (worldLevel >= MOB_LEVEL_CAPS.length) {
            return MOB_LEVEL_CAPS[MOB_LEVEL_CAPS.length - 1] + (worldLevel - MOB_LEVEL_CAPS.length + 1) * 60;
        }
        return MOB_LEVEL_CAPS[worldLevel];
    }

    // ["Default levelling settings"]
    public static int STARTING_LEVEL = 1;
    public static int MAXIMUM_LEVEL = 0; // 0 = no max level
    public static double LEVEL_INCREASE_PER_DISTANCE = 0.01;
    public static double LEVEL_INCREASE_PER_DEEPNESS = 0.0;
    public static int RANDOM_LEVEL_BONUS = 0;
    public static double LEVEL_BONUS_PER_DAY = 0.0;
    public static double LEVEL_POWER_PER_DISTANCE = 0.0;
    public static double LEVEL_POWER_PER_DEEPNESS = 0.0;

    // Dimension level modifiers / multipliers / fixed offsets
    public static class DimensionSetting {
        public double multiplier = 1.0;
        public int flatBonus = 0;
        public int fixedLevel = -1; // -1 means dynamic calculation

        public DimensionSetting(double multiplier, int flatBonus, int fixedLevel) {
            this.multiplier = multiplier;
            this.flatBonus = flatBonus;
            this.fixedLevel = fixedLevel;
        }
    }

    public static final Map<String, DimensionSetting> DIMENSION_SETTINGS = new HashMap<>();

    static {
        // Nether mobs start at level 50
        DIMENSION_SETTINGS.put("minecraft:the_nether", new DimensionSetting(1.0, 49, -1));
        // End mobs start at level 150
        DIMENSION_SETTINGS.put("minecraft:the_end", new DimensionSetting(1.0, 149, -1));
    }

    // [Attributes] - pairs of attribute ID and bonus per level
    public static final Map<String, Double> ATTRIBUTE_BONUSES = new LinkedHashMap<>();

    static {
        ATTRIBUTE_BONUSES.put("minecraft:generic.movement_speed", 0.001);
        ATTRIBUTE_BONUSES.put("minecraft:generic.flying_speed", 0.001);
        ATTRIBUTE_BONUSES.put("minecraft:generic.attack_damage", 0.1);
        ATTRIBUTE_BONUSES.put("minecraft:generic.armor", 0.1);
        ATTRIBUTE_BONUSES.put("minecraft:generic.max_health", 0.1);
    }

    public static boolean canLevelUp(ResourceLocation mobId) {
        if (mobId == null) return false;
        String idStr = mobId.toString();

        if (!WHITELIST.isEmpty()) {
            return WHITELIST.contains(idStr);
        }
        return !BLACKLIST.contains(idStr);
    }

    public static void setDimensionSetting(String dimensionId, double multiplier, int flatBonus, int fixedLevel) {
        DIMENSION_SETTINGS.put(dimensionId, new DimensionSetting(multiplier, flatBonus, fixedLevel));
    }
}
