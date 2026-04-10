package com.nhatbh.basedefensev2.events;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.registry.ModGameRules;
import com.nhatbh.basedefensev2.sanctity.duel.DuelManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID)
public class StrictPvPHandler {

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        Level level = event.getEntity().level();
        if (level.isClientSide()) return;

        if (ModGameRules.RULE_STRICT_PVP_DISABLED != null && level.getGameRules().getBoolean(ModGameRules.RULE_STRICT_PVP_DISABLED)) {
            LivingEntity target = event.getEntity();
            Entity attacker = event.getSource().getEntity(); 

            if (attacker != null) {
                if (attacker.equals(target)) return;

                Player attackerPlayer = getPlayerOwner(attacker);
                Player targetPlayer = getPlayerOwner(target);

                if (attackerPlayer != null && targetPlayer != null) {
                    // Check for active duel
                    if (DuelManager.isInDuel(attackerPlayer.getUUID(), targetPlayer.getUUID())) {
                        return; // Duel is active, allow damage
                    }
                    
                    event.setCanceled(true); 
                }
            }
        }
    }

    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        Level level = event.getEntity().level();
        if (level.isClientSide()) return;

        if (ModGameRules.RULE_STRICT_PVP_DISABLED != null && level.getGameRules().getBoolean(ModGameRules.RULE_STRICT_PVP_DISABLED)) {
            LivingEntity attacker = event.getEntity();
            LivingEntity target = event.getNewTarget();

            if (target != null) {
                Player attackerPlayer = getPlayerOwner(attacker);
                Player targetPlayer = getPlayerOwner(target);

                if (attackerPlayer != null && targetPlayer != null) {
                    // Check for active duel
                    if (DuelManager.isInDuel(attackerPlayer.getUUID(), targetPlayer.getUUID())) {
                        return; // Duel is active, allow targeting
                    }
                    
                    event.setCanceled(true); 
                }
            }
        }
    }

    private static Player getPlayerOwner(Entity entity) {
        if (entity instanceof Player player) return player;

        if (entity instanceof OwnableEntity ownable) {
            if (ownable.getOwner() instanceof Player player) return player;
            if (ownable.getOwnerUUID() != null) {
                return entity.level().getPlayerByUUID(ownable.getOwnerUUID());
            }
        }

        return null;
    }

}
