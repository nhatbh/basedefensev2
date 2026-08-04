package com.nhatbh.basedefensev2.boss.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ShockwaveEffect {

    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2);

    private static Method SET_BLOCK_STATE_METHOD;
    private static Method SET_INTERPOLATION_DURATION_METHOD;
    private static Method SET_INTERPOLATION_DELAY_METHOD;
    private static Method SET_BRIGHTNESS_METHOD;

    static {
        try {
            for (Method m : Display.BlockDisplay.class.getDeclaredMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == BlockState.class) {
                    SET_BLOCK_STATE_METHOD = m;
                    SET_BLOCK_STATE_METHOD.setAccessible(true);
                    break;
                }
            }
            for (Method m : Display.class.getDeclaredMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Brightness.class) {
                    SET_BRIGHTNESS_METHOD = m;
                    SET_BRIGHTNESS_METHOD.setAccessible(true);
                } else if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class) {
                    String name = m.getName().toLowerCase();
                    if (name.contains("duration") || name.contains("interpolationduration")) {
                        SET_INTERPOLATION_DURATION_METHOD = m;
                        SET_INTERPOLATION_DURATION_METHOD.setAccessible(true);
                    } else if (name.contains("delay") || name.contains("startdelta")
                            || name.contains("interpolationdelay")) {
                        SET_INTERPOLATION_DELAY_METHOD = m;
                        SET_INTERPOLATION_DELAY_METHOD.setAccessible(true);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Triggers an expanding block shockwave ripple outwards from the center using
     * BlockDisplay entities.
     *
     * @param level       The ServerLevel instance
     * @param center      The starting center point (e.g., Boss's BlockPos)
     * @param maxRadius   How far the wave expands in blocks
     * @param ringDelayMs Delay in milliseconds between each expanding ring
     * @param sourceBoss  The boss entity causing the earthquake
     */
    public static void createRipple(ServerLevel level, BlockPos center, int maxRadius, long ringDelayMs,
            LivingEntity sourceBoss) {
        for (int r = 1; r <= maxRadius; r++) {
            final int currentRadius = r;

            SCHEDULER.schedule(() -> {
                level.getServer().execute(() -> spawnRing(level, center, currentRadius, sourceBoss));
            }, r * ringDelayMs, TimeUnit.MILLISECONDS);
        }
    }

    private static void spawnRing(ServerLevel level, BlockPos center, int radius, LivingEntity sourceBoss) {
        level.playSound(null, center.getX() + 0.5, center.getY(), center.getZ() + 0.5,
                SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.2f, 0.5f);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {

                double distance = Math.sqrt(x * x + z * z);
                if (distance >= radius - 0.5 && distance <= radius + 0.5) {

                    BlockPos targetPos = center.offset(x, 0, z);

                    // Scan vertically around center.getY() to find solid floor block (bypassing barrier ceiling)
                    BlockPos groundPos = null;
                    int startY = center.getY() + 3;
                    for (int y = startY; y >= center.getY() - 6; y--) {
                        BlockPos checkPos = new BlockPos(targetPos.getX(), y, targetPos.getZ());
                        BlockState checkState = level.getBlockState(checkPos);
                        if (!checkState.isAir() && checkState.getFluidState().isEmpty() && !checkState.is(Blocks.BEDROCK) && !checkState.is(Blocks.BARRIER)) {
                            // Check if block above is air or non-solid so it's a surface
                            BlockState aboveState = level.getBlockState(checkPos.above());
                            if (aboveState.isAir() || aboveState.is(Blocks.BARRIER) || !aboveState.isCollisionShapeFullBlock(level, checkPos.above())) {
                                groundPos = checkPos;
                                break;
                            }
                        }
                    }

                    if (groundPos == null) {
                        continue;
                    }

                    BlockState state = level.getBlockState(groundPos);

                    // Spawn lightweight BlockDisplay visual ripple mesh
                    spawnDisplayBlockRipple(level, groundPos, state);

                    // Block break debris matching the popped block state + subtle impact sparks
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            groundPos.getX() + 0.5, groundPos.getY() + 1.0, groundPos.getZ() + 0.5,
                            3, 0.2, 0.2, 0.2, 0.1);
                    level.sendParticles(ParticleTypes.CRIT,
                            groundPos.getX() + 0.5, groundPos.getY() + 0.8, groundPos.getZ() + 0.5,
                            1, 0.2, 0.2, 0.2, 0.05);

                    // Apply powerful outward knockback/damage to ground entities (jumping avoids it!)
                    damageEntitiesOnRing(level, center, groundPos, sourceBoss);
                }
            }
        }
    }

    public static void spawnDisplayBlockRipple(ServerLevel level, BlockPos pos, BlockState state) {
        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null)
            return;

        // 1. Copy exact ambient light level of the block location (sampled at pos.above())
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos.above());
        int skyLight = level.getBrightness(LightLayer.SKY, pos.above());
        Brightness locationBrightness = new Brightness(blockLight, skyLight);

        try {
            if (SET_BRIGHTNESS_METHOD != null) {
                SET_BRIGHTNESS_METHOD.invoke(display, locationBrightness);
            }
            if (SET_BLOCK_STATE_METHOD != null) {
                SET_BLOCK_STATE_METHOD.invoke(display, state);
            }
            if (SET_INTERPOLATION_DURATION_METHOD != null) {
                SET_INTERPOLATION_DURATION_METHOD.invoke(display, 4); // Slower 4 ticks up (200ms)
            }
            if (SET_INTERPOLATION_DELAY_METHOD != null) {
                SET_INTERPOLATION_DELAY_METHOD.invoke(display, 0);
            }
        } catch (Exception ignored) {
        }

        display.setPos(pos.getX(), pos.getY(), pos.getZ());
        level.addFreshEntity(display);

        // 2. Animate UP by +0.6 blocks over 4 ticks (200ms)
        display.setOldPosAndRot();
        display.setPos(pos.getX(), pos.getY() + 0.6, pos.getZ());

        // 3. Animate DOWN back to original height over 4 ticks (at 200ms)
        SCHEDULER.schedule(() -> {
            level.getServer().execute(() -> {
                if (!display.isAlive())
                    return;
                try {
                    if (SET_INTERPOLATION_DURATION_METHOD != null) {
                        SET_INTERPOLATION_DURATION_METHOD.invoke(display, 4); // Slower 4 ticks down (200ms)
                    }
                    if (SET_INTERPOLATION_DELAY_METHOD != null) {
                        SET_INTERPOLATION_DELAY_METHOD.invoke(display, 0);
                    }
                } catch (Exception ignored) {
                }
                display.setOldPosAndRot();
                display.setPos(pos.getX(), pos.getY(), pos.getZ());
            });
        }, 200, TimeUnit.MILLISECONDS);

        // 4. Discard immediately as soon as down animation finishes (at 400ms)
        SCHEDULER.schedule(() -> {
            level.getServer().execute(display::discard);
        }, 400, TimeUnit.MILLISECONDS);
    }

    private static void damageEntitiesOnRing(ServerLevel level, BlockPos center, BlockPos pos, LivingEntity sourceBoss) {
        AABB box = new AABB(pos).inflate(0.5, 1.5, 0.5);

        // Check for Stone Spikes at this expanding ring position
        if (sourceBoss != null) {
            com.nhatbh.basedefensev2.boss.impl.generic.TitansMantleController controller = com.nhatbh.basedefensev2.boss.impl.generic.TitansMantleEventHandler.getController(sourceBoss);
            if (controller != null) {
                controller.getSpikeManager().checkAndShatterInRadius(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), 1.0);
            }
        }

        level.getEntitiesOfClass(Player.class, box).forEach(entity -> {
            if (!com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.canBeHitBySkill(entity))
                return;

            // The shockwave can be avoided by jumping!
            if (entity.onGround()) {
                float damage = (entity.getMaxHealth() * 0.20f) + 6.0f;
                entity.hurt(level.damageSources().mobAttack(sourceBoss != null ? sourceBoss : entity), damage);

                // Apply Heavy Footing
                com.nhatbh.basedefensev2.effects.HeavyFootingEffect.addStage(entity, 200, "Hit by Ground Shockwave Ripple");

                // Calculate direction pointing outward away from the center of the earthquake
                double dx = entity.getX() - (center.getX() + 0.5);
                double dz = entity.getZ() - (center.getZ() + 0.5);
                double dist = Math.sqrt(dx * dx + dz * dz);

                double dirX = dist > 0.001 ? (dx / dist) : 0.0;
                double dirZ = dist > 0.001 ? (dz / dist) : 0.0;

                // Massive outward horizontal launch + high vertical fling
                double horizontalPower = 3.5;
                double verticalPower = 1.2;

                entity.setDeltaMovement(dirX * horizontalPower, verticalPower, dirZ * horizontalPower);
                entity.hurtMarked = true;
                entity.hasImpulse = true;
            }
        });
    }

    /**
     * Triggers a straight line expanding block shockwave ripple along a direction vector.
     *
     * @param level       The ServerLevel instance
     * @param center      The starting point
     * @param direction   The travel direction vector
     * @param length      Travel distance in blocks (e.g. 40)
     * @param width       Width of the shockwave line in blocks (e.g. 5)
     * @param rowDelayMs  Delay between each traveling row (ms)
     * @param sourceBoss  The boss entity causing the shockwave
     */
    public static void createLineRipple(ServerLevel level, BlockPos center, Vec3 direction, int length, int width, long rowDelayMs, LivingEntity sourceBoss) {
        Vec3 normDir = new Vec3(direction.x, 0, direction.z).normalize();
        Vec3 perpDir = new Vec3(-normDir.z, 0, normDir.x).normalize();

        int halfWidth = width / 2;

        for (int d = 1; d <= length; d++) {
            final int step = d;

            SCHEDULER.schedule(() -> {
                level.getServer().execute(() -> {
                    level.playSound(null, center.getX() + normDir.x * step, center.getY(), center.getZ() + normDir.z * step,
                            SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.2f, 0.6f);

                    for (int w = -halfWidth; w <= halfWidth; w++) {
                        double offsetX = normDir.x * step + perpDir.x * w;
                        double offsetZ = normDir.z * step + perpDir.z * w;

                        BlockPos targetPos = center.offset((int) Math.round(offsetX), 0, (int) Math.round(offsetZ));

                        // Scan vertically around center.getY() to find solid floor block (bypassing barrier ceiling)
                        BlockPos groundPos = null;
                        int startY = center.getY() + 3;
                        for (int y = startY; y >= center.getY() - 6; y--) {
                            BlockPos checkPos = new BlockPos(targetPos.getX(), y, targetPos.getZ());
                            BlockState checkState = level.getBlockState(checkPos);
                            if (!checkState.isAir() && checkState.getFluidState().isEmpty() && !checkState.is(Blocks.BEDROCK) && !checkState.is(Blocks.BARRIER)) {
                                BlockState aboveState = level.getBlockState(checkPos.above());
                                if (aboveState.isAir() || aboveState.is(Blocks.BARRIER) || !aboveState.isCollisionShapeFullBlock(level, checkPos.above())) {
                                    groundPos = checkPos;
                                    break;
                                }
                            }
                        }

                        if (groundPos == null) {
                            continue;
                        }

                        BlockState state = level.getBlockState(groundPos);

                        // Spawn lightweight BlockDisplay visual ripple mesh
                        spawnDisplayBlockRipple(level, groundPos, state);

                        // Block debris particles
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                                groundPos.getX() + 0.5, groundPos.getY() + 1.0, groundPos.getZ() + 0.5,
                                2, 0.2, 0.2, 0.2, 0.1);

                        // Damage and DRAG hit players forward along normDir!
                        dragAndDamageEntities(level, normDir, groundPos, sourceBoss, step == length);
                    }
                });
            }, d * rowDelayMs, TimeUnit.MILLISECONDS);
        }
    }

    private static void dragAndDamageEntities(ServerLevel level, Vec3 travelDir, BlockPos pos, LivingEntity sourceBoss, boolean isFinalStep) {
        AABB box = new AABB(pos).inflate(1.5, 2.5, 1.5);
        level.getEntitiesOfClass(Player.class, box).forEach(entity -> {
            if (!com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.canBeHitBySkill(entity))
                return;

            // Reset invulnerability ticks so players are continuously dragged step-by-step
            if (entity.invulnerableTime > 2) {
                entity.invulnerableTime = 0;
            }

            // Damage player per row pulse: half max HP (2.0 HP = ~6.67%) + half flat (2.0) = 4.0 total damage on 30 HP player
            float damage = (entity.getMaxHealth() * (2.0f / 30.0f)) + 2.0f;
            entity.hurt(level.damageSources().mobAttack(sourceBoss != null ? sourceBoss : entity), damage);

            // Continuous dragging speed: keep low lift so player stays in the wave until final step
            double pushPower = isFinalStep ? 2.8 : 1.6;
            double liftPower = isFinalStep ? 0.85 : 0.15;

            entity.setDeltaMovement(travelDir.x * pushPower, liftPower, travelDir.z * pushPower);
            entity.hurtMarked = true;
            entity.hasImpulse = true;

            // Instantly send packet to ServerPlayer connection to override client movement prediction
            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
            }
        });
    }
}
