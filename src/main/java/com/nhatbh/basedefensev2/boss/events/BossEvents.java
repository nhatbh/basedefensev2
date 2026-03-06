package com.nhatbh.basedefensev2.boss.events;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

public class BossEvents {
    
    public static class PhaseAdvance extends Event {
        private final LivingEntity boss;
        private final int oldPhase;
        private final int newPhase;

        public PhaseAdvance(LivingEntity boss, int oldPhase, int newPhase) {
            this.boss = boss;
            this.oldPhase = oldPhase;
            this.newPhase = newPhase;
        }

        public LivingEntity getBoss() {
            return boss;
        }

        public int getOldPhase() {
            return oldPhase;
        }

        public int getNewPhase() {
            return newPhase;
        }
    }


    public static class ParrySuccessful extends Event {
        private final LivingEntity boss;

        public ParrySuccessful(LivingEntity boss) {
            this.boss = boss;
        }

        public LivingEntity getBoss() {
            return boss;
        }
    }
}
