package com.nhatbh.basedefensev2.boss.skills;

import com.nhatbh.basedefensev2.boss.core.AbstractBossEntity;

public interface PassiveSkill {
    /**
     * Called every tick for each passive skill active on the boss.
     */
    void tick(AbstractBossEntity boss);

    /**
     * Hook to modify damage, apply buffs/debuffs when added, etc.
     */
    default void onAdded(AbstractBossEntity boss) {}
    
    /**
     * Hook when the passive is removed (e.g. phase transition).
     */
    default void onRemoved(AbstractBossEntity boss) {}
}
