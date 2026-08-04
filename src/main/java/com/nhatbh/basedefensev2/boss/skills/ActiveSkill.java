package com.nhatbh.basedefensev2.boss.skills;

public class ActiveSkill {
    public enum Type { BASIC, TACTICAL }
    
    private final String id;
    private final String description;
    private final int cooldown;
    private final int globalCooldown;
    private final int startingCooldown;
    private final Type type;
    private final ActiveSequence sequence;

    private ActiveSkill(String id, String description, int cooldown, int globalCooldown, int startingCooldown, Type type, ActiveSequence sequence) {
        this.id = id;
        this.description = description;
        this.cooldown = cooldown;
        this.globalCooldown = globalCooldown;
        this.startingCooldown = startingCooldown;
        this.type = type;
        this.sequence = sequence;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description != null && !description.isEmpty() ? description : getDefaultDescription(id);
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
        private String description = "";
        private int cooldown = 0;
        private int globalCooldown = -1; // -1 means use default
        private int startingCooldown = 0;
        private Type type = Type.BASIC;
        private ActiveSequence sequence;

        public Builder(String id) {
            this.id = id;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
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
                finalGlobal = (type == Type.BASIC) ? 400 : 600; // 20s for BASIC, 30s for TACTICAL
            }
            
            return new ActiveSkill(id, description, cooldown, finalGlobal, startingCooldown, type, sequence);
        }
    }

    public static String getDefaultDescription(String skillId) {
        if (skillId == null) return "Performs a combat maneuver.";
        String cleanId = skillId.replaceAll("_p[0-9]+", "").replaceAll("_mb[0-9]+", "");
        return switch (cleanId) {
            case "flaming_dash" -> "Dashes forward unleashing a flame trail that ignites enemies.";
            case "explosive_dropkick" -> "Leaps high into the air and crashes down with an explosive shockwave.";
            case "earthquake" -> "Slam the ground, cracking terrain and knocking back nearby targets.";
            case "solar_cataclysm" -> "Channels solar energy to summon fiery meteor rain across the arena.";
            case "concentrated_laser" -> "Fires a concentrated beam of elemental laser that burns through shields.";
            case "static_shock" -> "Discharges high-voltage lightning sparks at surrounding targets.";
            case "stone_spike" -> "Summons jagged stone spikes out of the earth beneath targets.";
            case "sword_barrage" -> "Launches a barrage of spectral swords toward targeted players.";
            case "storm_lance" -> "Hurls a charged lightning lance that pierces targets.";
            case "glacial_prison" -> "Freezes the arena floor to encase players in solid ice blocks.";
            case "lance_of_light" -> "Calls down sacred light pillars dealing massive radiant damage.";
            case "vengeance_active" -> "Unleashes accumulated damage in a devastating vengeful blast.";
            default -> "Performs a high-impact boss combat ability.";
        };
    }
}
