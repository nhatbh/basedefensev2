package com.nhatbh.basedefensev2.stage.subsystem;

import com.mojang.logging.LogUtils;
import com.nhatbh.basedefensev2.stage.config.MobSpawnEntry;
import com.nhatbh.basedefensev2.stage.config.StageConfig;
import com.nhatbh.basedefensev2.stage.config.WaveConfig;
import com.nhatbh.basedefensev2.stage.core.StageContext;
import com.nhatbh.basedefensev2.stage.events.WaveEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Listens to {@link WaveEvents.SpawnRequested} and spawns each entry using
 * the configured formation.
 *
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │  FORMATION MODEL                                                      │
 * │                                                                       │
 * │  At the start of each wave a single random wave direction (0–2π) is  │
 * │  chosen. All entries with formation="arc" share that same direction   │
 * │  and the same arc_angle defined on the WaveConfig.                   │
 * │                                                                       │
 * │  Each mob is placed at:                                               │
 * │    θ = waveAngle + random_in( -arc_angle/2 , +arc_angle/2 )          │
 * │    d = random_in( distance_min , distance_max )                       │
 * │    x = origin.x + d · sin(θ)                                         │
 * │    z = origin.z + d · cos(θ)                                         │
 * │                                                                       │
 * │  Row placement: small distance = front/tanks, large = back/ranged.   │
 * └──────────────────────────────────────────────────────────────────────┘
 *
 * Friendly-fire prevention:
 *   After spawning, every entity is added to a shared scoreboard team
 *   ("bdv2_wave") with friendlyFire=false, preventing wave mobs from
 *   hurting each other via melee or projectile.  The team is wiped and
 *   rebuilt fresh on every wave to avoid stale members.
 */
@SuppressWarnings("deprecation")
public class SpawnerSubsystem {

    /** Scoreboard team name used for all arena wave mobs. */
    private static final String WAVE_TEAM = "bdv2_wave";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Random RANDOM  = new Random();

    @SubscribeEvent
    public void onSpawnRequested(WaveEvents.SpawnRequested event) {
        WaveConfig wave            = event.getWave();
        StageContext ctx           = event.getCtx();
        ServerLevel level          = event.getLevel();
        StageConfig.SpawnArea area = ctx.getActiveConfig().spawn_area;

        // One random direction shared by the entire wave
        double waveAngle = RANDOM.nextDouble() * 2.0 * Math.PI;
        double halfArc   = Math.toRadians(wave.arc_angle / 2.0);

        // Collect UUIDs (for StageContext) AND entity references (for team assignment)
        List<UUID>   uuids    = new ArrayList<>();
        List<Entity> entities = new ArrayList<>();

        for (MobSpawnEntry entry : wave.mobs) {
            if (entry.is_boss) {
                spawnBoss(entry, level, area, uuids, entities);
            } else {
                spawnMobs(entry, level, area, waveAngle, halfArc, uuids, entities);
            }
        }

        // Assign all wave mobs to the no-friendly-fire team
        assignToWaveTeam(level, entities);

        LOGGER.info("[SpawnerSubsystem] Wave '{}' spawned {} entities (dir={}° arc={}°)",
                wave.id, uuids.size(),
                (int) Math.toDegrees(waveAngle),
                (int) wave.arc_angle);

        ctx.registerEnemies(uuids);
    }

    // ── Friendly-fire prevention ──────────────────────────────────────────────

    /**
     * Creates (or resets) the arena wave scoreboard team and adds every
     * spawned entity to it.  Rebuilding the team on each wave ensures no
     * stale members carry over from previous waves.
     */
    private void assignToWaveTeam(ServerLevel level, List<Entity> entities) {
        if (entities.isEmpty()) return;

        Scoreboard scoreboard = level.getServer().getScoreboard();

        // Remove old team so stale members don't linger
        PlayerTeam oldTeam = scoreboard.getPlayerTeam(WAVE_TEAM);
        if (oldTeam != null) {
            scoreboard.removePlayerTeam(oldTeam);
        }

        // Create fresh team with friendly fire disabled
        PlayerTeam team = scoreboard.addPlayerTeam(WAVE_TEAM);
        team.setAllowFriendlyFire(false);
        team.setSeeFriendlyInvisibles(false);

        for (Entity entity : entities) {
            scoreboard.addPlayerToTeam(entity.getScoreboardName(), team);
        }

        LOGGER.info("[SpawnerSubsystem] Assigned {} entities to team '{}' (friendlyFire=false)",
                entities.size(), WAVE_TEAM);
    }

    // ── Position calculators ──────────────────────────────────────────────────

    private double[] arcPosition(StageConfig.SpawnArea origin,
                                 double waveAngle, double halfArc,
                                 MobSpawnEntry entry) {
        double spread = (RANDOM.nextDouble() * 2.0 - 1.0) * halfArc;
        double theta  = waveAngle + spread;
        double dist   = entry.distance_min
                + RANDOM.nextDouble() * Math.max(0, entry.distance_max - entry.distance_min);
        return new double[]{
                origin.x + dist * Math.sin(theta),
                origin.y,
                origin.z + dist * Math.cos(theta)
        };
    }

    private double[] randomPosition(StageConfig.SpawnArea origin) {
        double angle = RANDOM.nextDouble() * 2.0 * Math.PI;
        double r     = origin.radius * Math.sqrt(RANDOM.nextDouble());
        return new double[]{
                origin.x + r * Math.sin(angle),
                origin.y,
                origin.z + r * Math.cos(angle)
        };
    }

    // ── Spawners ──────────────────────────────────────────────────────────────

    private void spawnMobs(MobSpawnEntry entry, ServerLevel level,
                           StageConfig.SpawnArea area,
                           double waveAngle, double halfArc,
                           List<UUID> uuidsOut, List<Entity> entitiesOut) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(parseId(entry.type));
        if (type == null) {
            LOGGER.warn("[SpawnerSubsystem] Unknown entity type: {}", entry.type);
            return;
        }

        boolean isArc = "arc".equalsIgnoreCase(entry.formation);

        for (int i = 0; i < entry.count; i++) {
            double[] pos = isArc
                    ? arcPosition(area, waveAngle, halfArc, entry)
                    : randomPosition(area);

            Entity entity = type.create(level);
            if (entity == null) continue;

            entity.moveTo(pos[0], pos[1], pos[2], RANDOM.nextFloat() * 360f, 0f);
            
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
                mob.finalizeSpawn(level,
                        level.getCurrentDifficultyAt(BlockPos.containing(pos[0], pos[1], pos[2])),
                        MobSpawnType.EVENT, null, null);
            }

            level.addFreshEntity(entity);
            uuidsOut.add(entity.getUUID());
            entitiesOut.add(entity);
        }
    }

    private void spawnBoss(MobSpawnEntry entry, ServerLevel level,
                           StageConfig.SpawnArea area,
                           List<UUID> uuidsOut, List<Entity> entitiesOut) {
        if (entry.boss_id == null || entry.boss_id.isEmpty()) {
            LOGGER.warn("[SpawnerSubsystem] Boss entry has no boss_id; skipping.");
            return;
        }

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(parseId(entry.boss_id));
        if (type == null) {
            LOGGER.warn("[SpawnerSubsystem] Unknown boss entity type: {}", entry.boss_id);
            return;
        }

        Entity entity = type.create(level);
        if (entity == null) return;

        entity.moveTo(area.x, area.y, area.z, 0f, 0f);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.finalizeSpawn(level,
                    level.getCurrentDifficultyAt(BlockPos.containing(area.x, area.y, area.z)),
                    MobSpawnType.EVENT, null, null);
        }

        level.addFreshEntity(entity);
        uuidsOut.add(entity.getUUID());
        entitiesOut.add(entity);
        LOGGER.info("[SpawnerSubsystem] Spawned boss '{}' at origin.", entry.boss_id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ResourceLocation parseId(String id) {
        return id.contains(":") ? ResourceLocation.parse(id)
                : ResourceLocation.fromNamespaceAndPath("minecraft", id);
    }
}
