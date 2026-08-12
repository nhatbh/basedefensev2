package com.nhatbh.basedefensev2.boss.impl.spells;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

/**
 * Direct Iron's Spells API wrapper for boss spell casting.
 * Uses Mixins on SpellDamageSource so each created SpellDamageSource retains
 * its own independent custom damage stats.
 */
public class BossSpellCaster {

    private static boolean registered = false;

    public record DamageConfig(float flatDamage, float maxHpPercent, LivingEntity boss) {
    }

    private static final ThreadLocal<DamageConfig> ACTIVE_CAST_CONTEXT = new ThreadLocal<>();

    public static void init() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(BossSpellCaster.class);
            registered = true;
        }
    }

    /**
     * Casts a spell by string ID directly using Iron's Spells API.
     */
    public static boolean castSpell(LivingEntity boss, String spellId, int spellLevel, float flatDamage,
            float maxHpPercent) {
        if (boss == null || boss.level().isClientSide)
            return false;
        if (!ModList.get().isLoaded("irons_spellbooks"))
            return false;

        ResourceLocation location = new ResourceLocation(spellId);
        AbstractSpell spell = SpellRegistry.getSpell(location);

        if (spell == null || spell.getSpellId().equals(SpellRegistry.none().getSpellId()))
            return false;

        MagicData magicData = MagicData.getPlayerMagicData(boss);

        ACTIVE_CAST_CONTEXT.set(new DamageConfig(flatDamage, maxHpPercent, boss));

        try {
            spell.onCast(boss.level(), spellLevel, boss, CastSource.MOB, magicData);
        } finally {
            ACTIVE_CAST_CONTEXT.remove();
        }

        return true;
    }

    /**
     * Casts a spell from an arbitrary origin position aimed towards a target
     * position using an invisible proxy caster.
     */
    public static boolean castSpellFromLocation(LivingEntity boss, net.minecraft.world.phys.Vec3 originPos,
            net.minecraft.world.phys.Vec3 targetPos, String spellId, int spellLevel, float flatDamage,
            float maxHpPercent) {
        if (boss == null || boss.level().isClientSide || originPos == null || targetPos == null)
            return false;
        if (!ModList.get().isLoaded("irons_spellbooks"))
            return false;

        ResourceLocation location = new ResourceLocation(spellId);
        AbstractSpell spell = SpellRegistry.getSpell(location);

        if (spell == null || spell.getSpellId().equals(SpellRegistry.none().getSpellId()))
            return false;

        net.minecraft.world.phys.Vec3 dir = targetPos.subtract(originPos);
        if (dir.lengthSqr() < 0.0001) {
            dir = new net.minecraft.world.phys.Vec3(0, -1, 0);
        } else {
            dir = dir.normalize();
        }

        double d0 = dir.x;
        double d1 = dir.y;
        double d2 = dir.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);

        float yaw = (float) (Math.atan2(d2, d0) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Math.atan2(d1, d3) * (180.0D / Math.PI)));

        net.minecraft.world.entity.decoration.ArmorStand proxyCaster = net.minecraft.world.entity.EntityType.ARMOR_STAND
                .create(boss.level());
        if (proxyCaster == null)
            return false;

        proxyCaster.setPos(originPos.x, originPos.y, originPos.z);
        proxyCaster.setYRot(yaw);
        proxyCaster.setXRot(pitch);
        proxyCaster.yHeadRot = yaw;
        proxyCaster.yBodyRot = yaw;
        proxyCaster.setInvisible(true);
        proxyCaster.setNoGravity(true);

        MagicData magicData = MagicData.getPlayerMagicData(proxyCaster);

        ACTIVE_CAST_CONTEXT.set(new DamageConfig(flatDamage, maxHpPercent, boss));

        try {
            spell.onCast(boss.level(), spellLevel, proxyCaster, CastSource.MOB, magicData);
        } finally {
            ACTIVE_CAST_CONTEXT.remove();
            proxyCaster.discard();
        }

        return true;
    }

    public static boolean castSpellFromLocation(LivingEntity boss, net.minecraft.world.phys.Vec3 originPos,
            net.minecraft.world.phys.Vec3 targetPos, String spellId, float flatDamage, float maxHpPercent) {
        return castSpellFromLocation(boss, originPos, targetPos, spellId, 1, flatDamage, maxHpPercent);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        net.minecraft.world.entity.Entity entity = event.getEntity();
        if (entity == null || event.getLevel().isClientSide())
            return;

        DamageConfig context = ACTIVE_CAST_CONTEXT.get();
        if (context != null) {
            net.minecraft.nbt.CompoundTag nbt = entity.getPersistentData();
            nbt.putFloat("BD2_FlatDamage", context.flatDamage());
            nbt.putFloat("BD2_MaxHpPercent", context.maxHpPercent());

            if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
                if (context.boss() != null) {
                    projectile.setOwner(context.boss());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onSpellDamage(io.redspace.ironsspellbooks.api.events.SpellDamageEvent event) {
        float flatDmg = 0f;
        float pctDmg = 0f;
        boolean hasCustomDamage = false;

        // Check 1: Direct entity (e.g. Projectile entity NBT)
        net.minecraft.world.entity.Entity directEntity = event.getSpellDamageSource().getDirectEntity();
        if (directEntity != null && directEntity.getPersistentData().contains("BD2_MaxHpPercent")) {
            net.minecraft.nbt.CompoundTag nbt = directEntity.getPersistentData();
            flatDmg = nbt.getFloat("BD2_FlatDamage");
            pctDmg = nbt.getFloat("BD2_MaxHpPercent");
            hasCustomDamage = true;
        }
        // Check 2: Attacker entity NBT (for instant / non-projectile spells)
        else if (event.getSpellDamageSource().getEntity() != null
                && event.getSpellDamageSource().getEntity().getPersistentData().contains("BD2_MaxHpPercent")) {
            net.minecraft.nbt.CompoundTag nbt = event.getSpellDamageSource().getEntity().getPersistentData();
            flatDmg = nbt.getFloat("BD2_FlatDamage");
            pctDmg = nbt.getFloat("BD2_MaxHpPercent");
            hasCustomDamage = true;
        }

        if (hasCustomDamage) {
            if (event.getEntity() instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) event.getEntity();
                float maxHp = living.getMaxHealth();
                float addedPercentDamage = maxHp * pctDmg;
                float finalDamage = flatDmg + addedPercentDamage;

                if (finalDamage > 0) {
                    event.setAmount(finalDamage);
                }
            }
        }

        // Broadcast debug message to server
        if (event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // String logMsg = String.format(
            // "§e[SpellDamage RAW]§r Target: %s | HasCustomDmg: %b | DirectEntity: %s |
            // BaseAmount: %.2f | Flat: %.2f | Pct: %.2f%% => Final: %.2f",
            // event.getEntity().getName().getString(), hasCustomDamage, (directEntity !=
            // null ? directEntity.getClass().getSimpleName() : "null"), event.getAmount(),
            // flatDmg, pctDmg * 100f, event.getAmount()
            // );
            // serverLevel.getServer().getPlayerList().broadcastSystemMessage(
            // net.minecraft.network.chat.Component.literal(logMsg), false
            // );
        }
    }
}
