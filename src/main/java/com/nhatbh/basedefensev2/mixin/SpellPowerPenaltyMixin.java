package com.nhatbh.basedefensev2.mixin;

import com.nhatbh.basedefensev2.config.SpellPenaltyConfig;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSpell.class)
public abstract class SpellPowerPenaltyMixin {

    /**
     * Intercepts the Cast Time calculation.
     */
    @Inject(method = "getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I", at = @At("RETURN"), cancellable = true, remap = false)
    private void dynamicallyIncreaseCastTime(int spellLevel, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity == null)
            return;

        AbstractSpell spell = (AbstractSpell) (Object) this;
        int originalCastTime = cir.getReturnValue();

        // Don't apply cast time penalties to instant spells (0 ticks)
        if (originalCastTime <= 0)
            return;

        SpellPenaltyConfig.ConfigData cfg = SpellPenaltyConfig.data;
        double penaltyMultiplier = calculatePenaltyMultiplier(spell, entity, cfg.spellPenaltyWeight);

        if (penaltyMultiplier > 1.0) {
            int penalizedCastTime = (int) Math.ceil(originalCastTime * penaltyMultiplier);
            cir.setReturnValue(penalizedCastTime);
        }
    }

    /**
     * Intercepts canBeCastedBy to dynamically verify if the player has enough mana
     * after penalty.
     */
    @Inject(method = "canBeCastedBy", at = @At("HEAD"), cancellable = true, remap = false)
    private void dynamicallyCheckManaCost(int spellLevel, io.redspace.ironsspellbooks.api.spells.CastSource castSource,
            io.redspace.ironsspellbooks.api.magic.MagicData playerMagicData,
            net.minecraft.world.entity.player.Player player,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<io.redspace.ironsspellbooks.api.spells.CastResult> cir) {
        if (player == null)
            return;

        AbstractSpell spell = (AbstractSpell) (Object) this;
        int originalCost = spell.getManaCost(spellLevel);

        SpellPenaltyConfig.ConfigData cfg = SpellPenaltyConfig.data;
        double weight = spell.getEffectiveCastTime(spellLevel, player) <= 0 ? cfg.instantSpellPenaltyWeight
                : cfg.spellPenaltyWeight;
        double penaltyMultiplier = calculatePenaltyMultiplier(spell, player, weight);

        if (penaltyMultiplier > 1.0 && castSource.consumesMana()) {
            int penalizedCost = (int) Math.ceil(originalCost * penaltyMultiplier);
            boolean hasEnoughMana = playerMagicData.getMana() - penalizedCost >= 0;
            boolean hasRecastForSpell = playerMagicData.getPlayerRecasts().hasRecastForSpell(spell.getSpellId());

            if (!hasRecastForSpell && !hasEnoughMana) {
                // Return failure if they can't afford the penalized cost
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    int extraMana = penalizedCost - originalCost;
                    serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            String.format(
                                    "§cYour immense power demands more mana! (Cost: %d + %d)",
                                    originalCost, extraMana)));
                }
                cir.setReturnValue(new io.redspace.ironsspellbooks.api.spells.CastResult(
                        io.redspace.ironsspellbooks.api.spells.CastResult.Type.FAILURE,
                        net.minecraft.network.chat.Component
                                .translatable("ui.irons_spellbooks.cast_error_mana", spell.getDisplayName(player))
                                .withStyle(net.minecraft.ChatFormatting.RED)));
            }
        }
    }

    /**
     * Intercepts actual spell casting to consume the *penalized* mana amount,
     * bypassing the default deduction.
     */
    @Inject(method = "castSpell", at = @At("HEAD"), remap = false)
    private void dynamicallyConsumeManaOnCast(net.minecraft.world.level.Level world, int spellLevel,
            net.minecraft.server.level.ServerPlayer serverPlayer,
            io.redspace.ironsspellbooks.api.spells.CastSource castSource, boolean triggerCooldown,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (serverPlayer == null)
            return;

        AbstractSpell spell = (AbstractSpell) (Object) this;

        SpellPenaltyConfig.ConfigData cfg = SpellPenaltyConfig.data;
        double weight = spell.getEffectiveCastTime(spellLevel, serverPlayer) <= 0
                ? cfg.instantSpellPenaltyWeight
                : cfg.spellPenaltyWeight;
        double penaltyMultiplier = calculatePenaltyMultiplier(spell, serverPlayer, weight);
        if (penaltyMultiplier > 1.0 && castSource.consumesMana()) {
            io.redspace.ironsspellbooks.api.magic.MagicData playerMagicData = io.redspace.ironsspellbooks.api.magic.MagicData
                    .getPlayerMagicData(serverPlayer);
            boolean hasRecastForSpell = playerMagicData.getPlayerRecasts().hasRecastForSpell(spell.getSpellId());

            if (!hasRecastForSpell) {
                int originalCost = spell.getManaCost(spellLevel);
                int penalizedCost = (int) Math.ceil(originalCost * penaltyMultiplier);

                // Refund the original cost that castSpell *will* deduct (by artificially
                // increasing mana beforehand)
                // This is needed because castSpell will subtract getManaCost() right after this
                // HEAD inject.
                // It's cleaner to just subtract the *difference* in mana here.
                int extraCostDueToPenalty = penalizedCost - originalCost;

                float newMana = Math.max(playerMagicData.getMana() - extraCostDueToPenalty, 0);
                playerMagicData.setMana(newMana);
            }
        }
    }

    /**
     * Helper method to calculate how much excess power the player has.
     */
    private double calculatePenaltyMultiplier(AbstractSpell spell, LivingEntity entity, double weight) {
        double generalPower = 1.0;
        double schoolPower = 1.0;

        // Safely fetch General Spell Power
        if (entity.getAttribute(AttributeRegistry.SPELL_POWER.get()) != null) {
            generalPower = entity.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
        }

        // Safely fetch School-Specific Spell Power
        if (spell.getSchoolType() != null) {
            String schoolId = spell.getSchoolType().getId().getPath();
            Attribute schoolAttr = null;

            switch (schoolId) {
                case "fire":
                    schoolAttr = AttributeRegistry.FIRE_SPELL_POWER.get();
                    break;
                case "ice":
                    schoolAttr = AttributeRegistry.ICE_SPELL_POWER.get();
                    break;
                case "lightning":
                    schoolAttr = AttributeRegistry.LIGHTNING_SPELL_POWER.get();
                    break;
                case "holy":
                    schoolAttr = AttributeRegistry.HOLY_SPELL_POWER.get();
                    break;
                case "ender":
                    schoolAttr = AttributeRegistry.ENDER_SPELL_POWER.get();
                    break;
                case "blood":
                    schoolAttr = AttributeRegistry.BLOOD_SPELL_POWER.get();
                    break;
                case "evocation":
                    schoolAttr = AttributeRegistry.EVOCATION_SPELL_POWER.get();
                    break;
                case "nature":
                    schoolAttr = AttributeRegistry.NATURE_SPELL_POWER.get();
                    break;
                case "eldritch":
                    schoolAttr = AttributeRegistry.ELDRITCH_SPELL_POWER.get();
                    break;
            }

            if (schoolAttr != null && entity.getAttribute(schoolAttr) != null) {
                schoolPower = entity.getAttributeValue(schoolAttr);
            }
        }

        // Calculate bonus power (anything above the 1.0 base)
        double totalPowerBonus = Math.max(0, generalPower - 1.0) + Math.max(0, schoolPower - 1.0);
        double penalizedPowerBonus = Math.max(0, totalPowerBonus - SpellPenaltyConfig.data.penaltyThreshold);

        // Return a multiplier (e.g., if total bonus is 0.5 and weight is 1.5,
        // multiplier is 1.75x)
        return 1.0 + (penalizedPowerBonus * weight);
    }
}
