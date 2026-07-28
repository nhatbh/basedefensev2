package com.nhatbh.basedefensev2.boss.utils;

import net.minecraft.core.Rotations;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class WeaponProjectile {

    private final ServerLevel level;
    private final ArmorStand armorStand;
    private Vec3 position;
    private Vec3 velocity;
    private final LivingEntity owner;
    private final ItemStack itemStack;

    private boolean small = false;
    private boolean useHeadSlot = false;
    private float hitRadius = 1.0f;
    private int maxLifetime = 100;
    private int age = 0;

    private boolean gravity = false;
    private double gravityAccel = 0.03;

    private ParticleOptions trailParticle = null;
    private int trailParticleCount = 2;

    private BiConsumer<WeaponProjectile, Entity> onHitEntity;
    private Consumer<WeaponProjectile> onHitBlock;
    private Consumer<WeaponProjectile> onTick;
    private Predicate<Entity> targetFilter;

    private static final List<WeaponProjectile> ACTIVE_PROJECTILES = new ArrayList<>();

    public WeaponProjectile(ServerLevel level, Vec3 startPos, Vec3 velocity, LivingEntity owner, ItemStack itemStack) {
        this.level = level;
        this.position = startPos;
        this.velocity = velocity;
        this.owner = owner;
        this.itemStack = itemStack != null ? itemStack.copy() : ItemStack.EMPTY;

        this.armorStand = new ArmorStand(level, startPos.x, startPos.y, startPos.z);
        this.armorStand.setInvisible(true);
        this.armorStand.setNoGravity(true);
        this.armorStand.setNoBasePlate(true);
    }

    public static WeaponProjectile create(ServerLevel level, Vec3 startPos, Vec3 velocity, LivingEntity owner, ItemStack itemStack) {
        return new WeaponProjectile(level, startPos, velocity, owner, itemStack);
    }

    public WeaponProjectile setSmall(boolean small) {
        this.small = small;
        return this;
    }

    public WeaponProjectile setUseHeadSlot(boolean useHeadSlot) {
        this.useHeadSlot = useHeadSlot;
        return this;
    }

    public WeaponProjectile setHitRadius(float hitRadius) {
        this.hitRadius = hitRadius;
        return this;
    }

    public WeaponProjectile setMaxLifetime(int maxLifetime) {
        this.maxLifetime = maxLifetime;
        return this;
    }

    public WeaponProjectile setGravity(boolean gravity, double gravityAccel) {
        this.gravity = gravity;
        this.gravityAccel = gravityAccel;
        return this;
    }

    public WeaponProjectile setTrailParticle(ParticleOptions particle, int count) {
        this.trailParticle = particle;
        this.trailParticleCount = count;
        return this;
    }

    public WeaponProjectile setOnHitEntity(BiConsumer<WeaponProjectile, Entity> onHitEntity) {
        this.onHitEntity = onHitEntity;
        return this;
    }

    public WeaponProjectile setOnHitBlock(Consumer<WeaponProjectile> onHitBlock) {
        this.onHitBlock = onHitBlock;
        return this;
    }

    public WeaponProjectile setOnTick(Consumer<WeaponProjectile> onTick) {
        this.onTick = onTick;
        return this;
    }

    public WeaponProjectile setTargetFilter(Predicate<Entity> targetFilter) {
        this.targetFilter = targetFilter;
        return this;
    }

    public ArmorStand getArmorStand() {
        return armorStand;
    }

    public Vec3 getPosition() {
        return position;
    }

    public Vec3 getVelocity() {
        return velocity;
    }

    public LivingEntity getOwner() {
        return owner;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public void spawn() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        armorStand.saveWithoutId(tag);
        tag.putBoolean("Marker", true);
        tag.putBoolean("Small", small);
        tag.putBoolean("Invisible", true);
        tag.putBoolean("NoBasePlate", true);
        armorStand.load(tag);

        if (useHeadSlot) {
            armorStand.setItemSlot(EquipmentSlot.HEAD, itemStack);
        } else {
            armorStand.setItemSlot(EquipmentSlot.MAINHAND, itemStack);
        }

        updateRotation();
        level.addFreshEntity(armorStand);

        synchronized (ACTIVE_PROJECTILES) {
            ACTIVE_PROJECTILES.add(this);
        }
    }

    public void discard() {
        if (armorStand.isAlive()) {
            armorStand.discard();
        }
    }

    private float armPitchOffset = -90.0f;
    private float armYawOffset = 0.0f;
    private float armRollOffset = 0.0f;

    public WeaponProjectile setArmRotationOffset(float pitchOffset, float yawOffset, float rollOffset) {
        this.armPitchOffset = pitchOffset;
        this.armYawOffset = yawOffset;
        this.armRollOffset = rollOffset;
        return this;
    }

    private void updateRotation() {
        double dX = velocity.x;
        double dY = velocity.y;
        double dZ = velocity.z;
        double horizontalDistance = Math.sqrt(dX * dX + dZ * dZ);

        float yaw = (float) (Math.atan2(dZ, dX) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) (-(Math.atan2(dY, horizontalDistance) * (180.0 / Math.PI)));

        armorStand.setYRot(yaw);
        armorStand.setXRot(pitch);
        armorStand.setYBodyRot(yaw);
        armorStand.setYHeadRot(yaw);

        if (useHeadSlot) {
            armorStand.setHeadPose(new Rotations(pitch + armPitchOffset, armYawOffset, armRollOffset));
        } else {
            // Right arm pose raising arm (-90° base) + trajectory pitch so item in hand points straight in traveling direction
            armorStand.setRightArmPose(new Rotations(pitch + armPitchOffset, armYawOffset, armRollOffset));
        }
    }

    private boolean embedded = false;

    public void embedInGround(int durationTicks) {
        this.embedded = true;
        this.velocity = Vec3.ZERO;
        this.gravity = false;
        this.maxLifetime = this.age + durationTicks;
    }

    public boolean isEmbedded() {
        return embedded;
    }

    private boolean tick() {
        if (!armorStand.isAlive()) return false;

        age++;
        if (age >= maxLifetime) {
            if (embedded) {
                level.sendParticles(ParticleTypes.CRIT, position.x, position.y + 0.5, position.z, 6, 0.2, 0.2, 0.2, 0.05);
            }
            discard();
            return false;
        }

        if (embedded) {
            return true;
        }

        if (gravity) {
            velocity = velocity.add(0, -gravityAccel, 0);
        }

        Vec3 nextPos = position.add(velocity);

        // Update position and rotation
        position = nextPos;
        armorStand.moveTo(position.x, position.y, position.z, armorStand.getYRot(), armorStand.getXRot());
        updateRotation();

        // Trail particles
        if (trailParticle != null) {
            level.sendParticles(trailParticle, position.x, position.y + (small ? 0.5 : 1.0), position.z,
                    trailParticleCount, 0.1, 0.1, 0.1, 0.02);
        }

        if (onTick != null) {
            onTick.accept(this);
        }

        // Hit terrain check
        HitResult hitResult = level.clip(new net.minecraft.world.level.ClipContext(
                position, nextPos,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                armorStand));

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            if (onHitBlock != null) {
                onHitBlock.accept(this);
            }
            if (!embedded) {
                discard();
                return false;
            }
        }

        // Entity hit check
        AABB searchBox = new AABB(position.x - hitRadius, position.y - hitRadius, position.z - hitRadius,
                position.x + hitRadius, position.y + hitRadius, position.z + hitRadius);

        List<Entity> targets = level.getEntities(owner, searchBox, e -> {
            if (e == owner || e == armorStand || !e.isAlive()) return false;
            if (targetFilter != null && !targetFilter.test(e)) return false;
            return true;
        });

        if (!targets.isEmpty()) {
            Entity hitEntity = targets.get(0);
            if (onHitEntity != null) {
                onHitEntity.accept(this, hitEntity);
            }
            discard();
            return false;
        }

        return true;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;

        if (event.level instanceof ServerLevel serverLevel) {
            synchronized (ACTIVE_PROJECTILES) {
                Iterator<WeaponProjectile> it = ACTIVE_PROJECTILES.iterator();
                while (it.hasNext()) {
                    WeaponProjectile proj = it.next();
                    if (proj.level == serverLevel) {
                        if (!proj.tick()) {
                            it.remove();
                        }
                    }
                }
            }
        }
    }
}
