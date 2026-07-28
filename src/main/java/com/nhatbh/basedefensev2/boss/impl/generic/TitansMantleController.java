package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import com.nhatbh.basedefensev2.effects.HeavyFootingEffect;
import com.nhatbh.basedefensev2.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class TitansMantleController {

    private final LivingEntity boss;
    private final StoneSpikeManager spikeManager;

    // State & Meters
    private float pressure = 0.0f; // 0 - 100
    private boolean isRuptureActive = false;
    private int ruptureDurationTicks = 0;
    private int ruptureMaxTicks = 400; // 20s

    // Hit Cooldowns
    private final Map<UUID, Long> playerHitIcd = new HashMap<>();

    // Threshold Flags
    private boolean threshold75Passed = false;
    private boolean threshold50Passed = false;
    private boolean threshold25Passed = false;

    // Timers
    private int spikeCastTimer = 0;
    private int quakePulseTimer = 0;

    // Phase 3 Minion
    private IronGolem stoneGolem = null;

    public TitansMantleController(LivingEntity boss) {
        this.boss = boss;
        this.spikeManager = new StoneSpikeManager(boss);
        com.nhatbh.basedefensev2.boss.client.BossResourceBarRegistry.registerBar(
                boss,
                () -> isRuptureActive ? "Seismic Shield" : "Tectonic Pressure",
                () -> pressure,
                () -> 100f,
                () -> isRuptureActive ? 0xFFFF4500 : 0xFFFFAA00,
                () -> isRuptureActive ? 0xFF8B0000 : 0xFF884400
        );
    }

    public void tick() {
        if (boss == null || !boss.isAlive() || boss.level().isClientSide())
            return;
        ServerLevel level = (ServerLevel) boss.level();

        float hpPercent = boss.getHealth() / boss.getMaxHealth();

        // 1. Check HP Threshold Transitions
        if (!threshold75Passed && hpPercent <= 0.75f) {
            threshold75Passed = true;
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE,
                    1.5f, 0.7f);
        }

        if (!threshold50Passed && hpPercent <= 0.50f) {
            threshold50Passed = true;
            spawnStoneGolem(level);
        }

        if (!threshold25Passed && hpPercent <= 0.25f) {
            threshold25Passed = true;
            // Phase 4: Permanent Rupture
            activateRupture(true);
        }

        // 2. Minion Feed & Target Enforcement (Phase 3)
        if (stoneGolem != null && stoneGolem.isAlive()) {
            if (threshold50Passed && !isRuptureActive) {
                addPressure(0.1f); // +2 Pressure/s while Stone Golem is alive
            }

            // Enforce that Stone Golem ONLY targets living players
            LivingEntity target = stoneGolem.getTarget();
            if (!(target instanceof Player) || !target.isAlive() || ((Player) target).isCreative() || ((Player) target).isSpectator()) {
                Player nearestPlayer = level.getNearestPlayer(stoneGolem, 32.0);
                if (nearestPlayer != null && !nearestPlayer.isCreative() && !nearestPlayer.isSpectator()) {
                    stoneGolem.setTarget(nearestPlayer);
                } else {
                    stoneGolem.setTarget(null);
                }
            }
        }

        // 3. Passive Pressure Generation (+2/s base)
        if (!isRuptureActive && !threshold25Passed) {
            addPressure(0.1f);
        }

        // 4. Rupture Timer & Visuals
        if (isRuptureActive) {
            tickRupture(level);
        }

        // 6. Phase 4 Quake Pulse & Arena Cleansing Ticker (every 40s / 800 ticks)
        if (threshold25Passed) {
            quakePulseTimer++;
            if (quakePulseTimer >= 800) {
                quakePulseTimer = 0;
                triggerQuakePulse(level);
            }
        }

        // 7. Tick Stone Spikes
        spikeManager.tick();
        renderFloatingRockRing(level);
    }

    public void addPressure(float amount) {
        if (threshold25Passed)
            return;

        pressure = Math.min(100.0f, pressure + amount);
        if (pressure >= 100.0f && !isRuptureActive) {
            activateRupture(false);
        }
    }

    public void onBossDamaged(Player attacker, float amount) {
        long now = System.currentTimeMillis();
        long lastHit = playerHitIcd.getOrDefault(attacker.getUUID(), 0L);

        if (now - lastHit >= 200L) { // 0.2s ICD
            playerHitIcd.put(attacker.getUUID(), now);
            if (!isRuptureActive) {
                addPressure(1.0f);
            }
        }
    }

    public void activateRupture(boolean permanentPhase4) {
        if (isRuptureActive && !permanentPhase4)
            return;

        isRuptureActive = true;
        ruptureDurationTicks = permanentPhase4 ? 999999 : 400; // 20s
        ruptureMaxTicks = ruptureDurationTicks;
        pressure = 100.0f;

        boss.addEffect(new MobEffectInstance(ModEffects.SEISMIC_RUPTURE.get(), ruptureDurationTicks, 0, false, true));

        if (boss.level() instanceof ServerLevel level) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ANVIL_USE, SoundSource.HOSTILE,
                    2.0f, 0.6f);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    boss.getX(), boss.getY() + 1.0, boss.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
        }
    }

    public void handleHeavyHitOrParry(Player attacker, float damageAmount, boolean isParry) {
        if (!isRuptureActive || threshold25Passed)
            return;

        float bossMaxHp = boss.getMaxHealth() > 0 ? boss.getMaxHealth() : 100.0f;
        boolean isHeavyHit = damageAmount >= (bossMaxHp * 0.05f);

        if (isParry) {
            // Warrior parry counter = -30 Pressure
            pressure -= 30.0f;
            if (boss.level() instanceof ServerLevel level) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.SHIELD_BLOCK,
                        SoundSource.HOSTILE, 2.0f, 0.5f);
            }
        } else if (isHeavyHit) {
            // Heavy attack = -15 Pressure
            pressure -= 15.0f;
            if (boss.level() instanceof ServerLevel level) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ARMOR_EQUIP_IRON,
                        SoundSource.HOSTILE, 2.0f, 0.8f);
            }
        }

        if (pressure <= 0.0f) {
            pressure = 0.0f;
            endRupture(true); // Pressure Shatter!
        }
    }

    private void tickRupture(ServerLevel level) {
        if (!threshold25Passed) {
            ruptureDurationTicks--;
            if (ruptureDurationTicks <= 0) {
                endRupture(false); // Natural Expiry!
            }
        }
    }

    private void endRupture(boolean shatteredByHits) {
        isRuptureActive = false;
        boss.removeEffect(ModEffects.SEISMIC_RUPTURE.get());

        if (shatteredByHits) {
            // Shattered Mantle: 2s Stun + 8s Shattered Mantle (-50% Armor, +30% extra
            // damage taken)
            boss.addEffect(new MobEffectInstance(ModEffects.STAGGER.get(), 40, 0, false, true));
            boss.addEffect(new MobEffectInstance(ModEffects.SHATTERED_MANTLE.get(), 160, 0, false, true));

            if (boss.level() instanceof ServerLevel level) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ITEM_BREAK,
                        SoundSource.HOSTILE, 2.5f, 0.5f);
                level.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 1.0, boss.getZ(), 5, 0.5, 0.5,
                        0.5, 0);
            }
            pressure = 0.0f;
        } else {
            // Natural Expiry -> Resets Pressure to 0 & Casts Cataclysmic Stomp!
            pressure = 0.0f;
            castCataclysmicStomp();
        }
    }

    private static final java.util.concurrent.ScheduledExecutorService SCHEDULER = java.util.concurrent.Executors.newScheduledThreadPool(2);

    private void castCataclysmicStomp() {
        if (!(boss.level() instanceof ServerLevel level))
            return;

        BlockPos centerPos = boss.blockPosition();
        Vec3 bossPos = boss.position();

        // 3-Second Telegraph Warning (Sound & Particles)
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK,
                SoundSource.HOSTILE, 2.5f, 0.4f);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                SoundSource.HOSTILE, 2.5f, 0.4f);

        // Ground charging telegraph particles (30-Block Radius)
        ParticleUtils.renderCircle(level, ParticleTypes.FLAME, bossPos, 30.0, 96, 0.1);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                boss.getX(), boss.getY() + 0.5, boss.getZ(), 100, 3.0, 0.8, 3.0, 0.25);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, boss.getX(), boss.getY() + 0.2, boss.getZ(), 35, 1.5, 0.3, 1.5, 0.05);

        // Repeat telegraph pulse at 1.5s mark
        SCHEDULER.schedule(() -> {
            level.getServer().execute(() -> {
                if (boss.isAlive()) {
                    level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                            SoundSource.HOSTILE, 2.0f, 0.5f);
                    ParticleUtils.renderCircle(level, ParticleTypes.FLAME, boss.position(), 30.0, 96, 0.1);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                            boss.getX(), boss.getY() + 0.5, boss.getZ(), 80, 3.0, 0.8, 3.0, 0.2);
                }
            });
        }, 1500, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Blast Fires Exactly 3 Seconds (3000ms) After Telegraph! (30-Block Radius Ripple)
        SCHEDULER.schedule(() -> {
            level.getServer().execute(() -> {
                if (boss.isAlive()) {
                    level.playSound(null, centerPos.getX() + 0.5, centerPos.getY(), centerPos.getZ() + 0.5,
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.5f, 0.5f);
                    com.nhatbh.basedefensev2.boss.utils.ShockwaveEffect.createRipple(level, centerPos, 30, 40, boss);
                }
            });
        }, 3000, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void triggerQuakePulse(ServerLevel level) {
        BlockPos centerPos = boss.blockPosition();
        Vec3 bossPos = boss.position();

        // 3-Second Telegraph Warning (Sound & Particles)
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                SoundSource.HOSTILE, 2.0f, 0.5f);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 1.5f, 0.4f);

        ParticleUtils.renderCircle(level, ParticleTypes.CRIT, bossPos, 20.0, 64, 0.1);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                boss.getX(), boss.getY() + 0.5, boss.getZ(), 80, 2.0, 0.5, 2.0, 0.2);

        // Repeat telegraph pulse at 1.5s mark
        SCHEDULER.schedule(() -> {
            level.getServer().execute(() -> {
                if (boss.isAlive()) {
                    level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                            SoundSource.HOSTILE, 1.8f, 0.6f);
                    ParticleUtils.renderCircle(level, ParticleTypes.CRIT, boss.position(), 20.0, 64, 0.1);
                }
            });
        }, 1500, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Blast Fires Exactly 3 Seconds (3000ms) After Telegraph!
        SCHEDULER.schedule(() -> {
            level.getServer().execute(() -> {
                if (boss.isAlive()) {
                    level.playSound(null, centerPos.getX() + 0.5, centerPos.getY(), centerPos.getZ() + 0.5,
                            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.6f);
                    com.nhatbh.basedefensev2.boss.utils.ShockwaveEffect.createRipple(level, centerPos, 20, 40, boss);

                    // Arena Cleansing: Detonates ALL active Stone Spikes on the arena floor simultaneously!
                    spikeManager.detonateAllSpikes();

                    List<Player> hitPlayers = HitboxUtils.getEntitiesInCircle(level, Player.class, boss.position(), 20.0,
                            Player::isAlive);
                    for (Player p : hitPlayers) {
                        HeavyFootingEffect.addStage(p, 200, "Hit by Quake Pulse");
                    }
                }
            });
        }, 3000, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void spawnStoneGolem(ServerLevel level) {
        float golemHp = Math.max(30.0f, boss.getMaxHealth() * 0.10f);

        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        if (golem != null) {
            golem.moveTo(boss.getX() + 3.0, boss.getY(), boss.getZ(), 0, 0);
            golem.setCustomName(net.minecraft.network.chat.Component.literal("§8Stone Golem"));
            golem.setCustomNameVisible(true);

            var maxHpAttr = golem.getAttribute(Attributes.MAX_HEALTH);
            if (maxHpAttr != null) {
                maxHpAttr.setBaseValue(golemHp);
            }
            golem.setHealth(golemHp);
            golem.setPlayerCreated(false);
            golem.setGlowingTag(true);

            // AI Target Selector: ONLY target Players (never attack boss or other mobs)
            golem.targetSelector.removeAllGoals(g -> true);
            golem.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(golem, Player.class, true));

            level.addFreshEntity(golem);
            this.stoneGolem = golem;

            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE,
                    1.5f, 0.6f);
        }
    }

    private void renderFloatingRockRing(ServerLevel level) {
        if (!isRuptureActive && pressure <= 0.0f)
            return;

        Vec3 head = boss.getEyePosition().add(0, 0.4, 0);
        long tick = boss.tickCount;
        double radius = 1.6;
        int count = 10;

        double angleStep = 360.0 / count;
        for (int i = 0; i < count; i++) {
            double angleRad = Math.toRadians((i * angleStep) + (tick * 4));
            double dx = Math.cos(angleRad) * radius;
            double dz = Math.sin(angleRad) * radius;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                    head.x + dx, head.y, head.z + dz, 1, 0, 0, 0, 0.01);
        }
    }

    public StoneSpikeManager getSpikeManager() {
        return spikeManager;
    }

    public boolean isRuptureActive() {
        return isRuptureActive;
    }

    public void clear() {
        spikeManager.clear();
        if (stoneGolem != null && stoneGolem.isAlive()) {
            stoneGolem.discard();
        }
        com.nhatbh.basedefensev2.boss.client.BossResourceBarRegistry.unregisterBar(boss);
    }
}
