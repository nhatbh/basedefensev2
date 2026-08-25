package com.nhatbh.basedefensev2.boss.impl.spells;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpellDumper {

    private static final Map<String, String> MOD_LANG_CACHE = new HashMap<>();
    private static boolean langCacheLoaded = false;

    private static void ensureLangCacheLoaded() {
        if (langCacheLoaded) return;
        langCacheLoaded = true;

        try {
            for (var modInfo : ModList.get().getModFiles()) {
                try {
                    var file = modInfo.getFile();
                    if (file == null) continue;
                    var mods = modInfo.getMods();
                    for (var mod : mods) {
                        String modId = mod.getModId();
                        Path langDir = file.findResource("assets", modId, "lang");
                        if (Files.exists(langDir) && Files.isDirectory(langDir)) {
                            try (var stream = Files.list(langDir)) {
                                stream.filter(p -> p.toString().toLowerCase().endsWith(".json")).forEach(langPath -> {
                                    try (var reader = Files.newBufferedReader(langPath)) {
                                        JsonElement elem = JsonParser.parseReader(reader);
                                        if (elem.isJsonObject()) {
                                            JsonObject obj = elem.getAsJsonObject();
                                            for (String key : obj.keySet()) {
                                                MOD_LANG_CACHE.putIfAbsent(key, obj.get(key).getAsString());
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                });
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public static String dumpSpells() {
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            return "Iron's Spells and Spellbooks is not loaded!";
        }

        ensureLangCacheLoaded();

        try {
            Path outputPath = FMLPaths.CONFIGDIR.get().resolve("irons_spells_dump.json");
            File outFile = outputPath.toFile();

            List<AbstractSpell> spells = SpellRegistry.getEnabledSpells();

            JsonArray jsonArray = new JsonArray();

            Language lang = Language.getInstance();

            for (AbstractSpell spell : spells) {
                JsonObject obj = new JsonObject();
                obj.addProperty("spell_id", spell.getSpellId());

                String spellName = resolveTranslation(spell.getDisplayName(null).getString(), lang);
                obj.addProperty("name", spellName);
                
                if (spell.getSchoolType() != null) {
                    obj.addProperty("school_id", spell.getSchoolType().getId().toString());
                    String schoolName = resolveTranslation(spell.getSchoolType().getDisplayName().getString(), lang);
                    obj.addProperty("school_name", schoolName);
                }

                if (spell.getCastType() != null) {
                    obj.addProperty("cast_type", spell.getCastType().name());
                }

                obj.addProperty("min_level", spell.getMinLevel());
                obj.addProperty("max_level", spell.getMaxLevel());

                obj.addProperty("base_mana_cost", spell.getManaCost(spell.getMinLevel()));
                obj.addProperty("max_mana_cost", spell.getManaCost(spell.getMaxLevel()));

                obj.addProperty("base_cast_time_ticks", spell.getCastTime(spell.getMinLevel()));
                obj.addProperty("max_cast_time_ticks", spell.getCastTime(spell.getMaxLevel()));

                try {
                    obj.addProperty("rarity", spell.getRarity(spell.getMinLevel()).name());
                } catch (Throwable ignored) {}

                try {
                    obj.addProperty("allow_crafting", spell.allowCrafting());
                } catch (Throwable ignored) {}

                // Key for spell description in Iron's Spells is ComponentId + ".guide"
                String guideKey = spell.getComponentId() + ".guide";
                obj.addProperty("description_key", guideKey);

                String descText = resolveSpellDescription(spell, guideKey, spellName, lang);
                obj.addProperty("description", descText);

                jsonArray.add(obj);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            try (FileWriter writer = new FileWriter(outFile)) {
                gson.toJson(jsonArray, writer);
            }

            return "Exported " + jsonArray.size() + " spells to " + outFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error dumping spells: " + e.getMessage();
        }
    }

    private static String resolveTranslation(String keyOrText, Language lang) {
        if (keyOrText == null || keyOrText.isEmpty()) return "";
        if (MOD_LANG_CACHE.containsKey(keyOrText)) return MOD_LANG_CACHE.get(keyOrText);
        if (lang.has(keyOrText)) return lang.getOrDefault(keyOrText);
        return keyOrText;
    }

    private static String resolveSpellDescription(AbstractSpell spell, String guideKey, String spellName, Language lang) {
        String spellIdStr = spell.getSpellId();
        String domain = "irons_spellbooks";
        String path = spellIdStr;
        if (spellIdStr.contains(":")) {
            String[] parts = spellIdStr.split(":", 2);
            domain = parts[0];
            path = parts[1];
        }

        String[] candidateKeys = new String[] {
            guideKey,                                        // spell.<domain>.<path>.guide (Iron's Spells standard)
            spell.getComponentId() + ".desc",
            spell.getComponentId() + ".description",
            "spell." + domain + "." + path + ".guide",
            "spell." + domain + "." + path + ".desc",
            "spell." + domain + "." + path + ".description",
            "spell." + path + ".guide",
            "spell." + path + ".desc"
        };

        // 1. Check JAR lang cache for all candidate description keys
        for (String key : candidateKeys) {
            if (MOD_LANG_CACHE.containsKey(key)) {
                String val = MOD_LANG_CACHE.get(key);
                if (val != null && !val.trim().isEmpty() && !val.equalsIgnoreCase(spellName)) {
                    return val;
                }
            }
        }

        // 2. Check Minecraft Language manager
        for (String key : candidateKeys) {
            if (lang.has(key)) {
                String val = lang.getOrDefault(key);
                if (val != null && !val.trim().isEmpty() && !val.equalsIgnoreCase(spellName)) {
                    return val;
                }
            }
        }

        // 3. Try Component.translatable
        for (String key : candidateKeys) {
            String text = Component.translatable(key).getString();
            if (!text.equals(key) && !text.equalsIgnoreCase(spellName)) {
                return text;
            }
        }

        // 4. Reflection for specific description methods (excluding getDescription)
        String[] methodNames = new String[] { "getDescriptionId", "getSpellDescriptionId", "makeDescriptionId" };
        for (String mName : methodNames) {
            try {
                Method m = spell.getClass().getMethod(mName);
                Object res = m.invoke(spell);
                if (res instanceof String key) {
                    if (MOD_LANG_CACHE.containsKey(key)) return MOD_LANG_CACHE.get(key);
                    if (lang.has(key)) return lang.getOrDefault(key);
                }
            } catch (Throwable ignored) {}
        }

        return guideKey;
    }
}
