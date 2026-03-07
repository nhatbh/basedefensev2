package com.nhatbh.basedefensev2.boss.core;

import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BossDefinition {
    private final String id;
    private final EntityType<?> entityType;
    private final String baseEntity;
    private final BossStats baseStats;
    private final List<Element> elements;
    private final float maxPoise;
    private final float poiseDamageReduction;
    private final List<Phase> phases;

    private BossDefinition(Builder builder) {
        this.id = builder.id;
        this.entityType = builder.entityType;
        this.baseEntity = builder.baseEntity;
        this.baseStats = builder.statsBuilder.build();
        this.elements = builder.elements;
        this.maxPoise = builder.maxPoise;
        this.poiseDamageReduction = builder.poiseDamageReduction;
        this.phases = builder.phases;
    }

    public String getId() { return id; }
    public EntityType<?> getEntityType() { return entityType; }
    public String getBaseEntity() { return baseEntity; }
    public BossStats getBaseStats() { return baseStats; }
    public List<Element> getElements() { return elements; }
    public float getMaxPoise() { return maxPoise; }
    public float getPoiseDamageReduction() { return poiseDamageReduction; }
    public List<Phase> getPhases() { return phases; }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class BossStats {
        public final float health;
        public final float speed;
        public final float damage;
        
        public BossStats(float health, float speed, float damage) {
            this.health = health;
            this.speed = speed;
            this.damage = damage;
        }
    }

    public static class StatsBuilder {
        private float health = 100f;
        private float speed = 0.3f;
        private float damage = 5f;

        public StatsBuilder health(float health) { this.health = health; return this; }
        public StatsBuilder speed(float speed) { this.speed = speed; return this; }
        public StatsBuilder damage(float damage) { this.damage = damage; return this; }

        public BossStats build() {
            return new BossStats(health, speed, damage);
        }
    }

    public static class Builder {
        private final String id;
        private EntityType<?> entityType;
        private String baseEntity;
        private final StatsBuilder statsBuilder = new StatsBuilder();
        private final List<Element> elements = new ArrayList<>();
        private float maxPoise = 100f;
        private float poiseDamageReduction = 0f;
        private final List<Phase> phases = new ArrayList<>();

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

        public Builder elements(Element... elements) {
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

        public Builder phase(int phaseId, Consumer<Phase.Builder> consumer) {
            Phase.Builder pb = new Phase.Builder(phaseId);
            consumer.accept(pb);
            this.phases.add(pb.build());
            return this;
        }

        public BossDefinition build() {
            // Sort phases descending by HP threshold so we can easily check them
            this.phases.sort((p1, p2) -> Float.compare(p2.getHpThreshold(), p1.getHpThreshold()));
            return new BossDefinition(this);
        }
    }
}
