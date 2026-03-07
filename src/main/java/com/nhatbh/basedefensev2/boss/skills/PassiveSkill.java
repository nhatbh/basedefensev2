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
     * Hook when the passive is removed (e.g. phase transition).
     */
    default void onRemoved(LivingEntity boss) {}
}
