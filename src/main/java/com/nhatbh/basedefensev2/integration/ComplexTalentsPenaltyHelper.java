package com.nhatbh.basedefensev2.integration;

import com.complextalents.api.ComplexTalentsAPI;
import com.complextalents.api.leveling.ILevelingAPI;
import com.complextalents.api.origin.IOriginAPI;
import com.complextalents.api.skill.ISkillAPI;
import com.complextalents.api.spellmastery.ISpellMasteryAPI;
import com.complextalents.api.stats.IStatsAPI;
import com.complextalents.api.weaponmastery.IWeaponMasteryAPI;
import com.complextalents.leveling.data.LevelStats;
import com.complextalents.leveling.data.PlayerLevelingData;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ComplexTalentsPenaltyHelper {

    private static final Random RANDOM = new Random();

    /**
     * Applies soft game over stat penalties to a player:
     * - Resets purchased stats to origin baseline using resetStatsToOrigin
     * - Resets origin level to 1
     * - Resets active skill levels to 0
     * - Total XP reduced by 60% - 80% with 10 bonus early game SP awarded
     * - Weapon Mastery levels reduced by 60% - 80% (damage count preserved)
     * - 60% - 80% of learned spells randomly forgotten
     */
    public static void applySoftGameOverPenalties(ServerPlayer player) {
        if (!ModList.get().isLoaded("complextalents")) {
            return;
        }

        try {
            // Generate single randomized retained ratio between 0.20 and 0.40 (60% to 80% penalty loss)
            double lossPercentage = 0.60 + (RANDOM.nextDouble() * 0.20);
            double retainedRatio = 1.0 - lossPercentage;

            // 1. Reset Stat Matrix to active origin baseline
            IStatsAPI statsAPI = ComplexTalentsAPI.getStatsAPI();
            statsAPI.resetStatsToOrigin(player);

            // 2. Reset Origin Level to 1
            IOriginAPI originAPI = ComplexTalentsAPI.getOriginAPI();
            originAPI.setOriginLevel(player, 1);

            // 3. Reset Active Skill Levels to 0 for all equipped skill slots
            ISkillAPI skillAPI = ComplexTalentsAPI.getSkillAPI();
            for (int slot = 0; slot < 16; slot++) {
                ResourceLocation skillInSlot = skillAPI.getSkillInSlot(player, slot);
                if (skillInSlot != null) {
                    skillAPI.setSkillLevel(player, skillInSlot, 0);
                }
            }
            Set<ResourceLocation> learnedSkills = skillAPI.getAllLearnedSkills(player);
            if (learnedSkills != null) {
                for (ResourceLocation skillId : learnedSkills) {
                    skillAPI.setSkillLevel(player, skillId, 0);
                }
            }

            // 4. Leveling Penalty & SP Awarding
            ILevelingAPI levelingAPI = ComplexTalentsAPI.getLevelingAPI();
            LevelStats oldStats = levelingAPI.getStats(player);
            double oldTotalXP = oldStats.getTotalXP();
            double newTotalXP = oldTotalXP * retainedRatio;

            // Set new total XP via Leveling API (recalculates level, current XP progress, and total SP)
            levelingAPI.setTotalXP(player, newTotalXP);

            // Refund all skill points earned for the new level (since stats and skills were reset) + award 10 bonus early game SP
            PlayerLevelingData levelingData = PlayerLevelingData.get(player.getServer());
            levelingData.setConsumedSkillPoints(player.getUUID(), 0);
            levelingData.addSkillPoints(player.getUUID(), 10);

            // Sync updated level, XP, and available skill points to client
            com.complextalents.leveling.handlers.LevelingSyncHandler.syncPlayerLevelData(player);

            // 2. Weapon Mastery Penalty (Reduce level by 60-80%, KEEP damage count)
            IWeaponMasteryAPI weaponAPI = ComplexTalentsAPI.getWeaponMasteryAPI();
            Map<WeaponPath, Integer> masteryLevels = weaponAPI.getAllMasteryLevels(player);
            for (Map.Entry<WeaponPath, Integer> entry : masteryLevels.entrySet()) {
                WeaponPath path = entry.getKey();
                int oldLevel = entry.getValue();
                if (oldLevel > 0) {
                    int newMasteryLevel = (int) Math.round(oldLevel * retainedRatio);
                    weaponAPI.setMasteryLevel(player, path, newMasteryLevel);
                }
            }

            // 3. Spell Mastery Penalty (Forget 60-80% of learned spells & reduce school mastery)
            ISpellMasteryAPI spellAPI = ComplexTalentsAPI.getSpellMasteryAPI();
            Map<ResourceLocation, Integer> schoolMasteries = spellAPI.getAllMasteryLevels(player);
            for (Map.Entry<ResourceLocation, Integer> entry : schoolMasteries.entrySet()) {
                ResourceLocation schoolId = entry.getKey();
                int oldSchoolLevel = entry.getValue();
                if (oldSchoolLevel > 0) {
                    int newSchoolLevel = (int) Math.round(oldSchoolLevel * retainedRatio);
                    spellAPI.setMasteryLevel(player, schoolId, newSchoolLevel);
                }
            }

            Set<ResourceLocation> learnedSpells = spellAPI.getLearnedSpells(player);
            if (!learnedSpells.isEmpty()) {
                List<ResourceLocation> spellList = new ArrayList<>(learnedSpells);
                Collections.shuffle(spellList, RANDOM);
                int forgetCount = Math.min(spellList.size(), (int) Math.ceil(spellList.size() * lossPercentage));
                for (int i = 0; i < forgetCount; i++) {
                    spellAPI.forgetSpell(player, spellList.get(i));
                }
            }

            int percentLoss = (int) Math.round(lossPercentage * 100);
            player.sendSystemMessage(Component.literal("§c§l[STAT LOSS] §r§7Your stats and spell knowledge were damaged by " + percentLoss + "% in the collapse."));

        } catch (Throwable t) {
            // Log error if ComplexTalents penalty application fails
            player.getServer().sendSystemMessage(Component.literal("§cError applying ComplexTalents soft game over penalties: " + t.getMessage()));
        }
    }
}
