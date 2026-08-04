package com.nhatbh.basedefensev2.boss.skills;

import net.minecraft.world.entity.LivingEntity;

public interface PassiveSkill {
    /**
     * Called every tick for each passive skill active on the boss.
     */
    void tick(LivingEntity boss);

    /**
     * Hook to modify damage, apply buffs/debuffs when added, etc.
     */
    default void onAdded(LivingEntity boss) {}
    
    /**
     * Display name of the passive skill.
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Title prefix assigned to the boss when this passive is active (e.g. "Draconic", "Overclocked").
     */
    default String getTitlePrefix() {
        return "";
    }

    /**
     * Short description of what this passive skill does.
     */
    default String getDescription() {
        return "Grants unique combat passives and phase mechanics.";
    }

    /**
     * Hook when the passive is removed (e.g. phase transition).
     */
    default void onRemoved(LivingEntity boss) {}
}
