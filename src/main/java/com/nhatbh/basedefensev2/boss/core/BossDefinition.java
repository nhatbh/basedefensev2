package com.nhatbh.basedefensev2.boss.core;

import com.nhatbh.basedefensev2.elemental.ElementType;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class BossDefinition {
    private final String id;
    private final EntityType<?> entityType;
    private final String baseEntity;
    private final BossStats baseStats;
    private final List<ElementType> elements;
    private final float maxPoise;
    private final float poiseDamageReduction;
    private final float baseScale;
    private final List<Phase> phases;

    private BossDefinition(Builder builder) {
        this.id = builder.id;
        this.entityType = builder.entityType;
        this.baseEntity = builder.baseEntity;
        this.baseStats = builder.statsBuilder.build();
        this.elements = builder.elements;
        this.maxPoise = builder.maxPoise;
        this.poiseDamageReduction = builder.poiseDamageReduction;
        this.baseScale = builder.baseScale;
        this.phases = builder.phases;
    }

    public String getId() { return id; }
    public EntityType<?> getEntityType() { return entityType; }
    public String getBaseEntity() { return baseEntity; }
    public BossStats getBaseStats() { return baseStats; }
    public List<ElementType> getElements() { return elements; }
    public float getMaxPoise() { return maxPoise; }
    public float getPoiseDamageReduction() { return poiseDamageReduction; }
    public float getBaseScale() { return baseScale; }
    public List<Phase> getPhases() { return phases; }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class BossStats {
        public final float health;
        public final float speed;
        public final float damage;
        public final float knockbackResistance;
        
        public BossStats(float health, float speed, float damage, float knockbackResistance) {
            this.health = health;
            this.speed = speed;
            this.damage = damage;
            this.knockbackResistance = knockbackResistance;
        }
    }

    public static class StatsBuilder {
        private float health = 100f;
        private float speed = 0.3f;
        private float damage = 5f;
        private float knockbackResistance = 0f;

        public StatsBuilder health(float health) { this.health = health; return this; }
        public StatsBuilder speed(float speed) { this.speed = speed; return this; }
        public StatsBuilder damage(float damage) { this.damage = damage; return this; }
        public StatsBuilder knockbackResistance(float knockbackResistance) { this.knockbackResistance = knockbackResistance; return this; }

        public BossStats build() {
            return new BossStats(health, speed, damage, knockbackResistance);
        }
    }

    public static class Builder {
        private final String id;
        private EntityType<?> entityType;
        private String baseEntity;
        private final StatsBuilder statsBuilder = new StatsBuilder();
        private final List<ElementType> elements = new ArrayList<>();
        private float maxPoise = 100f;
        private float poiseDamageReduction = 0f;
        private float baseScale = 1.0f;
        private final List<Phase> phases = new ArrayList<>();
        private final List<com.nhatbh.basedefensev2.boss.skills.PassiveSkill> globalPassives = new ArrayList<>();
        private final List<Phase.ActiveSkillEntry> globalActives = new ArrayList<>();

        public Builder(String id) {
            this.id = id;
        }

        public Builder entity(EntityType<?> type) {
            this.entityType = type;
            return this;
        }

        public Builder baseEntity(String baseEntity) {
            this.baseEntity = baseEntity;
            return this;
        }

        public Builder baseStats(Consumer<StatsBuilder> consumer) {
            consumer.accept(this.statsBuilder);
            return this;
        }

        public Builder elements(ElementType... elements) {
            this.elements.addAll(List.of(elements));
            return this;
        }

        public Builder maxPoise(float maxPoise) {
            this.maxPoise = maxPoise;
            return this;
        }

        public Builder poiseDamageReduction(float reduction) {
            this.poiseDamageReduction = reduction;
            return this;
        }

        public Builder baseScale(float scale) {
            this.baseScale = scale;
            return this;
        }

        public Builder addPassive(com.nhatbh.basedefensev2.boss.skills.PassiveSkill passive) {
            this.globalPassives.add(passive);
            return this;
        }

        public Builder addGlobalPassive(com.nhatbh.basedefensev2.boss.skills.PassiveSkill passive) {
            return addPassive(passive);
        }

        public Builder addActive(com.nhatbh.basedefensev2.boss.skills.ActiveSkill skill, Function<net.minecraft.world.entity.LivingEntity, Integer> priorityCondition) {
            this.globalActives.add(new Phase.ActiveSkillEntry(skill, priorityCondition));
            return this;
        }

        public Builder addGlobalActive(com.nhatbh.basedefensev2.boss.skills.ActiveSkill skill, Function<net.minecraft.world.entity.LivingEntity, Integer> priorityCondition) {
            return addActive(skill, priorityCondition);
        }

        public Builder phase(int phaseId, Consumer<Phase.Builder> consumer) {
            Phase.Builder pb = new Phase.Builder(phaseId);
            consumer.accept(pb);
            this.phases.add(pb.build());
            return this;
        }

        public BossDefinition build() {
            // Apply global passives and actives to all phases if present
            for (Phase phase : this.phases) {
                for (com.nhatbh.basedefensev2.boss.skills.PassiveSkill passive : globalPassives) {
                    if (!phase.getPassives().contains(passive)) {
                        phase.getPassives().add(passive);
                    }
                }
                for (Phase.ActiveSkillEntry active : globalActives) {
                    boolean exists = phase.getActives().stream()
                            .anyMatch(a -> a.skill.getId().equals(active.skill.getId()));
                    if (!exists) {
                        phase.getActives().add(active);
                    }
                }
            }
            // Sort phases descending by HP threshold so we can easily check them
            this.phases.sort((p1, p2) -> Float.compare(p2.getHpThreshold(), p1.getHpThreshold()));
            return new BossDefinition(this);
        }
    }
}
