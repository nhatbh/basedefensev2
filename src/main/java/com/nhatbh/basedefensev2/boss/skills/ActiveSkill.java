package com.nhatbh.basedefensev2.boss.skills;

public class ActiveSkill {
    public enum Type { BASIC, TACTICAL }
    
    private final String id;
    private final int cooldown;
    private final int globalCooldown;
    private final int startingCooldown;
    private final Type type;
    private final ActiveSequence sequence;

    private ActiveSkill(String id, int cooldown, int globalCooldown, int startingCooldown, Type type, ActiveSequence sequence) {
        this.id = id;
        this.cooldown = cooldown;
        this.globalCooldown = globalCooldown;
        this.startingCooldown = startingCooldown;
        this.type = type;
        this.sequence = sequence;
    }

    public String getId() {
        return id;
    }

    public int getCooldown() {
        return cooldown;
    }

    public int getGlobalCooldown() {
        return globalCooldown;
    }

    public int getStartingCooldown() {
        return startingCooldown;
    }

    public Type getType() {
        return type;
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
        private int globalCooldown = -1; // -1 means use default
        private int startingCooldown = 0;
        private Type type = Type.BASIC;
        private ActiveSequence sequence;

        public Builder(String id) {
            this.id = id;
        }

        public Builder cooldown(int ticks) {
            this.cooldown = ticks;
            return this;
        }

        public Builder globalCooldown(int ticks) {
            this.globalCooldown = ticks;
            return this;
        }

        public Builder startingCooldown(int ticks) {
            this.startingCooldown = ticks;
            return this;
        }

        public Builder type(Type type) {
            this.type = type;
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
            
            int finalGlobal = globalCooldown;
            if (finalGlobal < 0) {
                finalGlobal = (type == Type.BASIC) ? 60 : 600; // 3s for BASIC, 30s for TACTICAL
            }
            
            return new ActiveSkill(id, cooldown, finalGlobal, startingCooldown, type, sequence);
        }
    }
}
