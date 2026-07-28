package com.nhatbh.basedefensev2.elemental;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MobElementConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("elements.json");

    public static ConfigData data = new ConfigData();

    public static class ConfigData {
        public boolean enabled = true;
        public Map<String, String> entity_element_mappings = new HashMap<>();
    }

    public static void load() {
        File configFile = CONFIG_PATH.toFile();
        if (!configFile.exists()) {
            generateDefaultConfig();
            save();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            data = GSON.fromJson(reader, ConfigData.class);
            if (data == null) {
                data = new ConfigData();
            }
            if (data.entity_element_mappings == null) {
                data.entity_element_mappings = new HashMap<>();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load MobElementConfig", e);
            generateDefaultConfig();
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save MobElementConfig", e);
        }
    }

    private static void generateDefaultConfig() {
        data.enabled = true;

        // alexscaves
        data.entity_element_mappings.put("alexscaves:boundroid", "LIGHTNING");
        data.entity_element_mappings.put("alexscaves:boundroid_winch", "LIGHTNING");
        data.entity_element_mappings.put("alexscaves:brainiac", "EVOCATION");
        data.entity_element_mappings.put("alexscaves:caniac", "FIRE");
        data.entity_element_mappings.put("alexscaves:caramel_cube", "NATURE");
        data.entity_element_mappings.put("alexscaves:corrodent", "NATURE");
        data.entity_element_mappings.put("alexscaves:deep_one", "AQUA");
        data.entity_element_mappings.put("alexscaves:deep_one_knight", "AQUA");
        data.entity_element_mappings.put("alexscaves:deep_one_mage", "AQUA");
        data.entity_element_mappings.put("alexscaves:ferrouslime", "LIGHTNING");
        data.entity_element_mappings.put("alexscaves:forsaken", "ELDRITCH");
        data.entity_element_mappings.put("alexscaves:gingerbread_man", "NATURE");
        data.entity_element_mappings.put("alexscaves:gum_worm", "NATURE");
        data.entity_element_mappings.put("alexscaves:gumbeeper", "LIGHTNING");
        data.entity_element_mappings.put("alexscaves:licowitch", "ELDRITCH");
        data.entity_element_mappings.put("alexscaves:luxtructosaurus", "FIRE");
        data.entity_element_mappings.put("alexscaves:magnetron", "LIGHTNING");
        data.entity_element_mappings.put("alexscaves:mine_guardian", "LIGHTNING");
        data.entity_element_mappings.put("alexscaves:nucleeper", "LIGHTNING");
        data.entity_element_mappings.put("alexscaves:teletor", "LIGHTNING");
        data.entity_element_mappings.put("alexscaves:underzealot", "ELDRITCH");
        data.entity_element_mappings.put("alexscaves:vesper", "BLOOD");
        data.entity_element_mappings.put("alexscaves:watcher", "ELDRITCH");

        // aquamirae
        data.entity_element_mappings.put("aquamirae:captain_cornelia", "ICE");
        data.entity_element_mappings.put("aquamirae:eel", "AQUA");
        data.entity_element_mappings.put("aquamirae:maw", "AQUA");
        data.entity_element_mappings.put("aquamirae:maze_mother", "AQUA");
        data.entity_element_mappings.put("aquamirae:pillagers_patrol", "ICE");
        data.entity_element_mappings.put("aquamirae:tortured_soul", "ICE");

        // bbv
        data.entity_element_mappings.put("bbv:crystal", "ENDER");
        data.entity_element_mappings.put("bbv:eyeofenderentity", "ENDER");
        data.entity_element_mappings.put("bbv:fireball", "FIRE");
        data.entity_element_mappings.put("bbv:fireballfall", "FIRE");
        data.entity_element_mappings.put("bbv:planet", "ENDER");
        data.entity_element_mappings.put("bbv:playerplaced_crystal", "ENDER");
        data.entity_element_mappings.put("bbv:portal", "ENDER");
        data.entity_element_mappings.put("bbv:the_ender_dragon", "ENDER");
        data.entity_element_mappings.put("bbv:the_ender_dragonoverworld", "ENDER");

        // block_factorys_bosses
        data.entity_element_mappings.put("block_factorys_bosses:cinematic_kraken", "AQUA");
        data.entity_element_mappings.put("block_factorys_bosses:crossbow_pirate", "PHYSICAL");
        data.entity_element_mappings.put("block_factorys_bosses:dragon_guard_sword", "FIRE");
        data.entity_element_mappings.put("block_factorys_bosses:flaming_skeleton_guard_fireball", "FIRE");
        data.entity_element_mappings.put("block_factorys_bosses:flaming_skeleton_guard_sword", "FIRE");
        data.entity_element_mappings.put("block_factorys_bosses:ghost_tentacle", "AQUA");
        data.entity_element_mappings.put("block_factorys_bosses:infernal_dragon", "FIRE");
        data.entity_element_mappings.put("block_factorys_bosses:kraken", "AQUA");
        data.entity_element_mappings.put("block_factorys_bosses:kraken_tentacle", "AQUA");
        data.entity_element_mappings.put("block_factorys_bosses:pile_of_bones", "BLOOD");
        data.entity_element_mappings.put("block_factorys_bosses:pirate_captain", "AQUA");
        data.entity_element_mappings.put("block_factorys_bosses:pirate_rook", "PHYSICAL");
        data.entity_element_mappings.put("block_factorys_bosses:sandworm", "NATURE");
        data.entity_element_mappings.put("block_factorys_bosses:soul_knight_wither_skeleton", "BLOOD");
        data.entity_element_mappings.put("block_factorys_bosses:soul_skeleton", "ICE");
        data.entity_element_mappings.put("block_factorys_bosses:underworld_knight", "ELDRITCH");
        data.entity_element_mappings.put("block_factorys_bosses:yeti", "ICE");

        // bosses_of_miracle
        data.entity_element_mappings.put("bosses_of_miracle:azrath_the_berserk", "BLOOD");
        data.entity_element_mappings.put("bosses_of_miracle:kenji_the_blade_master", "LIGHTNING");
        data.entity_element_mappings.put("bosses_of_miracle:kenshin_the_overlord_shogun", "EVOCATION");

        // cataclysm
        data.entity_element_mappings.put("cataclysm:amethyst_crab", "EVOCATION");
        data.entity_element_mappings.put("cataclysm:ancient_ancient_remnant", "EVOCATION");
        data.entity_element_mappings.put("cataclysm:ancient_remnant", "EVOCATION");
        data.entity_element_mappings.put("cataclysm:aptrgangr", "ICE");
        data.entity_element_mappings.put("cataclysm:cindaria", "FIRE");
        data.entity_element_mappings.put("cataclysm:clawdian", "AQUA");
        data.entity_element_mappings.put("cataclysm:coral_golem", "AQUA");
        data.entity_element_mappings.put("cataclysm:coralssus", "AQUA");
        data.entity_element_mappings.put("cataclysm:deepling", "AQUA");
        data.entity_element_mappings.put("cataclysm:deepling_angler", "AQUA");
        data.entity_element_mappings.put("cataclysm:deepling_brute", "AQUA");
        data.entity_element_mappings.put("cataclysm:deepling_priest", "AQUA");
        data.entity_element_mappings.put("cataclysm:deepling_warlock", "AQUA");
        data.entity_element_mappings.put("cataclysm:draugr", "ICE");
        data.entity_element_mappings.put("cataclysm:drowned_host", "AQUA");
        data.entity_element_mappings.put("cataclysm:elite_draugr", "ICE");
        data.entity_element_mappings.put("cataclysm:ender_golem", "ENDER");
        data.entity_element_mappings.put("cataclysm:ender_guardian", "ENDER");
        data.entity_element_mappings.put("cataclysm:endermaptera", "ENDER");
        data.entity_element_mappings.put("cataclysm:hippocamtus", "AQUA");
        data.entity_element_mappings.put("cataclysm:ignis", "FIRE");
        data.entity_element_mappings.put("cataclysm:ignited_berserker", "FIRE");
        data.entity_element_mappings.put("cataclysm:ignited_revenant", "FIRE");
        data.entity_element_mappings.put("cataclysm:kobolediator", "EVOCATION");
        data.entity_element_mappings.put("cataclysm:koboleton", "EVOCATION");
        data.entity_element_mappings.put("cataclysm:lionfish", "AQUA");
        data.entity_element_mappings.put("cataclysm:maledictus", "ELDRITCH");
        data.entity_element_mappings.put("cataclysm:nameless_sorcerer", "ELDRITCH");
        data.entity_element_mappings.put("cataclysm:netherite_monstrosity", "FIRE");
        data.entity_element_mappings.put("cataclysm:old_netherite_monstrosity", "FIRE");
        data.entity_element_mappings.put("cataclysm:royal_draugr", "ICE");
        data.entity_element_mappings.put("cataclysm:scylla", "AQUA");
        data.entity_element_mappings.put("cataclysm:symbiocto", "AQUA");
        data.entity_element_mappings.put("cataclysm:the_harbinger", "LIGHTNING");
        data.entity_element_mappings.put("cataclysm:the_leviathan", "AQUA");
        data.entity_element_mappings.put("cataclysm:the_prowler", "EVOCATION");
        data.entity_element_mappings.put("cataclysm:the_watcher", "ELDRITCH");
        data.entity_element_mappings.put("cataclysm:urchinkin", "AQUA");
        data.entity_element_mappings.put("cataclysm:wadjet", "FIRE");

        // cataclysm_spellbooks
        data.entity_element_mappings.put("cataclysm_spellbooks:extended_death_laser_beam", "ELDRITCH");
        data.entity_element_mappings.put("cataclysm_spellbooks:extended_laser_beam", "EVOCATION");
        data.entity_element_mappings.put("cataclysm_spellbooks:phantom_ancient_remnant", "EVOCATION");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_amethyst_crab", "EVOCATION");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_aptrgangr", "ICE");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_clawdian", "AQUA");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_coral_golem", "AQUA");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_coralssus", "AQUA");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_counterspell_watcher", "ELDRITCH");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_draugur", "ICE");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_elite_draugur", "ICE");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_ignited_berserker", "FIRE");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_ignited_revenant", "FIRE");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_koboldiator", "EVOCATION");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_koboleton", "EVOCATION");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_prowler", "EVOCATION");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_royal_draugur", "ICE");
        data.entity_element_mappings.put("cataclysm_spellbooks:summoned_watcher", "ELDRITCH");

        // combat_evolution
        data.entity_element_mappings.put("combat_evolution:shelmarow", "NATURE");

        // efn
        data.entity_element_mappings.put("efn:doppelganger", "ENDER");
        data.entity_element_mappings.put("efn:guardian", "HOLY");

        // epicfight
        data.entity_element_mappings.put("epicfight:wither_ghost", "BLOOD");
        data.entity_element_mappings.put("epicfight:wither_skeleton_minion", "BLOOD");

        // fdbosses
        data.entity_element_mappings.put("fdbosses:fire_malkuth_warrior", "FIRE");
        data.entity_element_mappings.put("fdbosses:ice_malkuth_warrior", "ICE");

        // iceandfire
        data.entity_element_mappings.put("iceandfire:dread_beast", "ICE");
        data.entity_element_mappings.put("iceandfire:dread_ghoul", "ICE");
        data.entity_element_mappings.put("iceandfire:dread_horse", "ICE");
        data.entity_element_mappings.put("iceandfire:dread_knight", "ICE");
        data.entity_element_mappings.put("iceandfire:dread_lich", "ICE");
        data.entity_element_mappings.put("iceandfire:dread_scuttler", "ICE");
        data.entity_element_mappings.put("iceandfire:dread_thrall", "ICE");
        data.entity_element_mappings.put("iceandfire:ghost", "ICE");
        data.entity_element_mappings.put("iceandfire:troll", "NATURE");

        // irons_spellbooks
        data.entity_element_mappings.put("irons_spellbooks:apothecarist", "NATURE");
        data.entity_element_mappings.put("irons_spellbooks:archevoker", "EVOCATION");
        data.entity_element_mappings.put("irons_spellbooks:catacombs_zombie", "BLOOD");
        data.entity_element_mappings.put("irons_spellbooks:citadel_keeper", "HOLY");
        data.entity_element_mappings.put("irons_spellbooks:cryomancer", "ICE");
        data.entity_element_mappings.put("irons_spellbooks:cultist", "BLOOD");
        data.entity_element_mappings.put("irons_spellbooks:cursed_armor_stand", "EVOCATION");
        data.entity_element_mappings.put("irons_spellbooks:dead_king", "BLOOD");
        data.entity_element_mappings.put("irons_spellbooks:debug_wizard", "EVOCATION");
        data.entity_element_mappings.put("irons_spellbooks:fire_boss", "FIRE");
        data.entity_element_mappings.put("irons_spellbooks:ice_spider", "ICE");
        data.entity_element_mappings.put("irons_spellbooks:magehunter_vindicator", "EVOCATION");
        data.entity_element_mappings.put("irons_spellbooks:necromancer", "BLOOD");
        data.entity_element_mappings.put("irons_spellbooks:pyromancer", "FIRE");
        data.entity_element_mappings.put("irons_spellbooks:summoned_skeleton", "BLOOD");
        data.entity_element_mappings.put("irons_spellbooks:summoned_zombie", "BLOOD");

        // minecraft
        data.entity_element_mappings.put("minecraft:blaze", "FIRE");
        data.entity_element_mappings.put("minecraft:cave_spider", "NATURE");
        data.entity_element_mappings.put("minecraft:creeper", "LIGHTNING");
        data.entity_element_mappings.put("minecraft:drowned", "AQUA");
        data.entity_element_mappings.put("minecraft:elder_guardian", "AQUA");
        data.entity_element_mappings.put("minecraft:ender_dragon", "ENDER");
        data.entity_element_mappings.put("minecraft:enderman", "ENDER");
        data.entity_element_mappings.put("minecraft:endermite", "ENDER");
        data.entity_element_mappings.put("minecraft:evoker", "EVOCATION");
        data.entity_element_mappings.put("minecraft:ghast", "FIRE");
        data.entity_element_mappings.put("minecraft:giant", "BLOOD");
        data.entity_element_mappings.put("minecraft:guardian", "AQUA");
        data.entity_element_mappings.put("minecraft:hoglin", "NATURE");
        data.entity_element_mappings.put("minecraft:husk", "FIRE");
        data.entity_element_mappings.put("minecraft:illusioner", "EVOCATION");
        data.entity_element_mappings.put("minecraft:magma_cube", "FIRE");
        data.entity_element_mappings.put("minecraft:phantom", "LIGHTNING");
        data.entity_element_mappings.put("minecraft:piglin", "FIRE");
        data.entity_element_mappings.put("minecraft:piglin_brute", "FIRE");
        data.entity_element_mappings.put("minecraft:pillager", "EVOCATION");
        data.entity_element_mappings.put("minecraft:ravager", "EVOCATION");
        data.entity_element_mappings.put("minecraft:shulker", "ENDER");
        data.entity_element_mappings.put("minecraft:silverfish", "NATURE");
        data.entity_element_mappings.put("minecraft:skeleton", "ICE");
        data.entity_element_mappings.put("minecraft:slime", "NATURE");
        data.entity_element_mappings.put("minecraft:spider", "NATURE");
        data.entity_element_mappings.put("minecraft:stray", "ICE");
        data.entity_element_mappings.put("minecraft:vex", "EVOCATION");
        data.entity_element_mappings.put("minecraft:vindicator", "EVOCATION");
        data.entity_element_mappings.put("minecraft:warden", "ELDRITCH");
        data.entity_element_mappings.put("minecraft:witch", "NATURE");
        data.entity_element_mappings.put("minecraft:wither", "ELDRITCH");
        data.entity_element_mappings.put("minecraft:wither_skeleton", "BLOOD");
        data.entity_element_mappings.put("minecraft:zoglin", "BLOOD");
        data.entity_element_mappings.put("minecraft:zombie", "NATURE");
        data.entity_element_mappings.put("minecraft:zombie_villager", "NATURE");
        data.entity_element_mappings.put("minecraft:zombified_piglin", "BLOOD");

        // traveloptics
        data.entity_element_mappings.put("traveloptics:aqua_grandmaster", "AQUA");
        data.entity_element_mappings.put("traveloptics:aquamancer", "AQUA");
        data.entity_element_mappings.put("traveloptics:enraged_dead_king", "BLOOD");
        data.entity_element_mappings.put("traveloptics:summoned_aptrgangr", "ICE");
        data.entity_element_mappings.put("traveloptics:summoned_clawdian", "AQUA");
        data.entity_element_mappings.put("traveloptics:summoned_deepling", "AQUA");
        data.entity_element_mappings.put("traveloptics:summoned_draugr", "ICE");
        data.entity_element_mappings.put("traveloptics:summoned_elite_draugr", "ICE");
        data.entity_element_mappings.put("traveloptics:summoned_ender_golem", "ENDER");
        data.entity_element_mappings.put("traveloptics:summoned_ignited_berserker", "FIRE");
        data.entity_element_mappings.put("traveloptics:summoned_ignited_revenant", "FIRE");
        data.entity_element_mappings.put("traveloptics:summoned_kobolediator", "EVOCATION");
        data.entity_element_mappings.put("traveloptics:summoned_koboleton", "EVOCATION");
        data.entity_element_mappings.put("traveloptics:summoned_magnetron", "LIGHTNING");
        data.entity_element_mappings.put("traveloptics:summoned_royal_draugr", "ICE");
        data.entity_element_mappings.put("traveloptics:summoned_the_prowler", "EVOCATION");
        data.entity_element_mappings.put("traveloptics:summoned_the_watcher", "ELDRITCH");
        data.entity_element_mappings.put("traveloptics:summoned_wadjet", "FIRE");
        data.entity_element_mappings.put("traveloptics:test_wizard", "EVOCATION");
        data.entity_element_mappings.put("traveloptics:the_nightwarden", "ELDRITCH");

        // wom
        data.entity_element_mappings.put("wom:evil_skeleton", "BLOOD");
        data.entity_element_mappings.put("wom:hollow", "ELDRITCH");
        data.entity_element_mappings.put("wom:lycanth", "NATURE");
        data.entity_element_mappings.put("wom:saulomonk", "HOLY");
    }

    public static ElementType getElementFor(String entityId) {
        if (!data.enabled)
            return null;
        String typeStr = data.entity_element_mappings.get(entityId);
        if (typeStr != null) {
            return ElementType.fromString(typeStr);
        }
        return null;
    }
}
