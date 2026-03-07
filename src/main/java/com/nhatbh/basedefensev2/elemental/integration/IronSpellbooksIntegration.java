package com.nhatbh.basedefensev2.elemental.integration;

import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.elemental.events.ElementalDamageEvent;
import com.nhatbh.basedefensev2.strength.EntityEvents;
import com.nhatbh.basedefensev2.strength.EntityStrengthData;
import com.nhatbh.basedefensev2.strength.network.EntityStrengthSyncPacket;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class IronSpellbooksIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @SubscribeEvent
    public static void onSpellDamage(io.redspace.ironsspellbooks.api.events.SpellDamageEvent event) {
        SpellDamageSource spellDamageSource = event.getSpellDamageSource();
        if (spellDamageSource == null) return;
        
        io.redspace.ironsspellbooks.api.spells.AbstractSpell spell = spellDamageSource.spell();
        if (spell == null) return;

        ElementType sourceElement = mapSchoolTypeToElement(spell.getSchoolType());
        if (sourceElement == null) return;

        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide) return;

        LivingEntity caster = event.getEntity().getKillCredit();

        String elementStr = entity.getPersistentData().getString("ElementType");
        ElementType targetElement = ElementType.fromString(elementStr);

        float damage = event.getAmount();
        float originalDamage = damage;
        String spellName = spell.getSpellName();

        // Get strength data
        EntityStrengthData data = EntityStrengthData.get(entity);
        if (data == null) return; // Mobs without strength System are handled normally

        boolean isExhausted = data.currentStrength <= 0;
        float currentStrength = data.currentStrength;
        float maxStrength = data.maxStrength;

        boolean isArcaneSchool = isArcaneSchool(sourceElement);
        boolean isElementalSchool = isElementalSchool(sourceElement);

        // 1. Arcane Backfire
        if (isArcaneSchool && targetElement != null && sourceElement == targetElement) {
            handleArcaneBackfire(entity, damage, data);
            event.setCanceled(true); 
            logDamage(entity, spellName, sourceElement, targetElement, originalDamage, originalDamage, damage, " §5(BACKFIRE)");
            LOGGER.debug("Arcane backfire triggered on {} by {}", entity.getName().getString(), spellName);
            return;
        }

        float strengthBefore = currentStrength;
        float strengthDamage = damage;
        String arcaneEffect = "";

        if (isElementalSchool) {
            float multiplier = getElementalMultiplier(sourceElement, targetElement);
            strengthDamage = damage * multiplier;
            if (multiplier != 1.0f) {
                arcaneEffect = multiplier > 1.0f ? " §a(COUNTER x2.0)" : " §c(RESISTED x0.5)";
            }
        } else if (isArcaneSchool) {
            switch (sourceElement) {
                case HOLY -> {
                    if (maxStrength > 0) {
                        float scaleFactor = 1.0f + (currentStrength / maxStrength);
                        strengthDamage = damage * scaleFactor;
                        arcaneEffect = String.format(" §e(SMITE x%.2f)", scaleFactor);
                    }
                }
                case EVOCATION -> {
                    if (maxStrength > 0) {
                        float missingStrength = maxStrength - currentStrength;
                        float scaleFactor = 1.0f + (missingStrength / maxStrength);
                        strengthDamage = damage * scaleFactor;
                        arcaneEffect = String.format(" §6(SOUL REND x%.2f)", scaleFactor);
                    }
                }
                case ENDER -> {
                    if (isExhausted) {
                        damage = originalDamage * 2.0f;
                        strengthDamage = 0;
                        arcaneEffect = " §5(PURE MAGIC x2.0 HP)";
                    } else {
                        strengthDamage = damage * 0.5f;
                        arcaneEffect = " §8(ENDER x0.5 STR)";
                    }
                }
                case ELDRITCH -> {
                    strengthDamage = 0;
                    arcaneEffect = " §4(DECAY - BYPASS)";
                }
                case BLOOD -> {
                    float lifestealAmount = damage * 0.25f;
                    if (caster != null && lifestealAmount > 0) {
                        caster.heal(lifestealAmount);
                        arcaneEffect = String.format(" §c(LIFESTEAL +%.1f HP)", lifestealAmount);
                    }
                }
                default -> {}
            }
        }

        if (!isExhausted) {
            data.currentStrength = Math.max(0, data.currentStrength - strengthDamage);
            
            // Trigger exhaustion
            if (data.currentStrength <= 0) {
                data.recoveryTicks = 300;
                if (BossManager.isBoss(entity)) {
                    // BossManager handles this generically via LivingDamageEvent
                }
                MinecraftForge.EVENT_BUS.post(new EntityEvents.PoiseBroken(entity));
            }

            // Apply strength damage mitigation to HP
            if (data.isPercentageBased) {
                damage = damage * (1.0f - data.reductionValue);
            } else {
                damage = Math.max(0, damage - data.reductionValue);
            }
            
            data.save(entity);
            syncStrength(entity, data);
        }

        // Tag entity to skip HP damage doubling logic from LivingHurtEvent if needed
        entity.getPersistentData().putBoolean("SkipStrengthDamage", true);
        event.setAmount(damage);

        logDamage(entity, spellName, sourceElement, targetElement, originalDamage, strengthDamage, damage, arcaneEffect);
    }
    
    private static void logDamage(LivingEntity entity, String spellName, ElementType src, ElementType tgt, float raw, float strDmg, float hpDmg, String effect) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : serverLevel.players()) {
                player.sendSystemMessage(Component.literal(String.format(
                        "§d[Spell] §7%s hit by §f%s §7(StrDmg: %.1f%s, HpDmg: %.1f)",
                        entity.getName().getString(), spellName, strDmg, effect, hpDmg)));
            }
        }
    }

    private static void handleArcaneBackfire(LivingEntity entity, float damage, EntityStrengthData data) {
        float newStrength = Math.min(data.maxStrength, data.currentStrength + damage);
        data.currentStrength = newStrength;
        data.save(entity);
        syncStrength(entity, data);
    }

    private static boolean isElementalSchool(ElementType element) {
        if (element == null) return false;
        return switch (element) {
            case FIRE, NATURE, AQUA, LIGHTNING, ICE -> true;
            default -> false;
        };
    }

    private static boolean isArcaneSchool(ElementType element) {
        if (element == null) return false;
        return switch (element) {
            case HOLY, EVOCATION, ENDER, ELDRITCH, BLOOD -> true;
            default -> false;
        };
    }

    private static float getElementalMultiplier(ElementType source, ElementType target) {
        if (target == null || source == null) return 1.0f;
        if (!isElementalSchool(source) || !isElementalSchool(target)) return 1.0f;

        if (source == ElementType.FIRE && target == ElementType.NATURE) return 2.0f;
        if (source == ElementType.NATURE && target == ElementType.AQUA) return 2.0f;
        if (source == ElementType.AQUA && target == ElementType.LIGHTNING) return 2.0f;
        if (source == ElementType.LIGHTNING && target == ElementType.ICE) return 2.0f;
        if (source == ElementType.ICE && target == ElementType.FIRE) return 2.0f;

        if (source == ElementType.NATURE && target == ElementType.FIRE) return 0.5f;
        if (source == ElementType.AQUA && target == ElementType.NATURE) return 0.5f;
        if (source == ElementType.LIGHTNING && target == ElementType.AQUA) return 0.5f;
        if (source == ElementType.ICE && target == ElementType.LIGHTNING) return 0.5f;
        if (source == ElementType.FIRE && target == ElementType.ICE) return 0.5f;

        return 1.0f;
    }

    private static ElementType mapSchoolTypeToElement(SchoolType schoolType) {
        if (schoolType == null) return null;
        String schoolPath = schoolType.getId().getPath();
        return switch (schoolPath) {
            case "fire" -> ElementType.FIRE;
            case "ice" -> ElementType.ICE;
            case "lightning" -> ElementType.LIGHTNING;
            case "ender" -> ElementType.ENDER;
            case "nature" -> ElementType.NATURE;
            case "blood" -> ElementType.BLOOD;
            case "holy" -> ElementType.HOLY;
            case "evocation" -> ElementType.EVOCATION;
            case "eldritch" -> ElementType.ELDRITCH;
            default -> null; 
        };
    }

    private static void syncStrength(LivingEntity entity, EntityStrengthData data) {
         NetworkManager.sendToTracking(
             new EntityStrengthSyncPacket(entity.getId(), data.currentStrength, data.maxStrength, data.recoveryTicks),
             entity
         );
    }
    
    @SubscribeEvent
    public static void onMobEffectAdded(MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        MobEffectInstance effectInstance = event.getEffectInstance();
        if (effectInstance == null) return;

        String descriptionId = effectInstance.getEffect().getDescriptionId();
        if ("effect.traveloptics.wet".equals(descriptionId)) {
            MinecraftForge.EVENT_BUS.post(new ElementalDamageEvent(target, target, ElementType.AQUA, 0));
        }
    }
}
