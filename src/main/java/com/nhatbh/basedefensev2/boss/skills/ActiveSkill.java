package com.nhatbh.basedefensev2.boss.skills;

public class ActiveSkill {
    private final String id;
    private final int cooldown;
    private final ActiveSequence sequence;

    private ActiveSkill(String id, int cooldown, ActiveSequence sequence) {
        this.id = id;
        this.cooldown = cooldown;
        this.sequence = sequence;
    }

    public String getId() {
        return id;
    }

    public int getCooldown() {
        return cooldown;
    }

    public ActiveSequence getSequence() {
        return sequence;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private int cooldown = 0;
        private ActiveSequence sequence;

        public Builder(String id) {
            this.id = id;
        }

        public Builder cooldown(int ticks) {
            this.cooldown = ticks;
            return this;
        }

        public Builder sequence(ActiveSequence sequence) {
            this.sequence = sequence;
            return this;
        }

        public ActiveSkill build() {
            if (sequence == null) {
                throw new IllegalStateException("ActiveSkill requires an ActiveSequence");
            }
            return new ActiveSkill(id, cooldown, sequence);
        }
    }
}
