package com.nhatbh.basedefensev2.boss.core;

import com.nhatbh.basedefensev2.boss.events.BossEvents;
import com.nhatbh.basedefensev2.boss.network.EntitySkillSyncPacket;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.boss.skills.BossSkillData;
import com.nhatbh.basedefensev2.boss.skills.SkillIndicatorData;
import com.nhatbh.basedefensev2.strength.EntityEvents;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class BossManager {
    // Basic weak registry mapping living entities to their boss components.
    private static final Map<LivingEntity, BossComponent> BOSS_REGISTRY = new WeakHashMap<>();

    public static void registerBoss(LivingEntity entity, BossComponent component) {
        BOSS_REGISTRY.put(entity, component);
        initializeBossAttributes(entity, component.getDefinition());
        component.initialize(entity);
        if (component.getCurrentPhase() != null) {
            syncMount(entity, component, component.getCurrentPhase());
            syncWeapon(entity, component.getCurrentPhase());
        }
    }

    public static void initializeBossAttributes(LivingEntity boss, BossDefinition def) {
        if (def.getBaseStats() != null) {
            var atts = boss.getAttributes();
            var healthAttr = atts.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(def.getBaseStats().health);
                boss.setHealth(def.getBaseStats().health);
            }
            var speedAttr = atts.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(def.getBaseStats().speed);
            }
            var damageAttr = atts.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.setBaseValue(def.getBaseStats().damage);
            }
            var followAttr = atts.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            if (followAttr != null) {
                followAttr.setBaseValue(300.0);
            }
        }
    }

    public static BossComponent get(LivingEntity entity) {
        return BOSS_REGISTRY.get(entity);
    }

    public static boolean isBoss(LivingEntity entity) {
        return BOSS_REGISTRY.containsKey(entity);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity boss = event.getEntity();
        if (boss.level().isClientSide()) return;

        BossComponent comp = get(boss);
        if (comp == null) return;

        if (comp.getExhaustionTicks() > 0) {
            comp.setExhaustionTicks(comp.getExhaustionTicks() - 1);
            return;
        }

        checkPhaseTransition(boss, comp);

        // Tick skill cooldowns
        for (Map.Entry<String, Integer> entry : comp.getSkillCooldowns().entrySet()) {
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - 1);
            }
        }

        Phase phase = comp.getCurrentPhase();
        if (phase != null) {
            phase.tickPassives(boss);
        }

        if (comp.getCurrentSequence() != null && comp.getCurrentSequence().isRunning()) {
            comp.getCurrentSequence().tick(boss);
        } else {
            comp.setCurrentSequence(null);
            ActiveSkill nextSkill = SkillEvaluator.getHighestPrioritySkill(comp, boss, phase);
            if (nextSkill != null) {
                comp.setSkillCooldown(nextSkill.getId(), nextSkill.getCooldown());
                comp.setCurrentSequence(nextSkill.getSequence().start(boss));
            }
        }
    }

    private static void checkPhaseTransition(LivingEntity boss, BossComponent comp) {
        BossDefinition def = comp.getDefinition();
        if (def.getPhases().isEmpty()) return;
        
        float hpPercent = boss.getHealth() / boss.getMaxHealth();
        
        for (int i = 0; i < def.getPhases().size(); i++) {
            Phase phase = def.getPhases().get(i);
            if (i > comp.getCurrentPhaseIndex() && hpPercent <= phase.getHpThreshold()) {
                transitionToPhase(boss, comp, i, phase);
                break;
            }
        }
    }

    private static void transitionToPhase(LivingEntity boss, BossComponent comp, int newIndex, Phase newPhase) {
        if (comp.getCurrentPhase() != null) {
            comp.getCurrentPhase().onExit(boss);
        }
        
        int oldPhaseId = comp.getCurrentPhase() != null ? comp.getCurrentPhase().getId() : -1;
        comp.setCurrentPhaseIndex(newIndex);
        comp.setCurrentPhase(newPhase);
        
        // Let clients know - later we sync this via packet
        boss.getPersistentData().putInt("BossPhaseIndex", newIndex);
        
        MinecraftForge.EVENT_BUS.post(new BossEvents.PhaseAdvance(boss, oldPhaseId, newPhase.getId()));
        comp.setCurrentSequence(null); // Interrupted by phase shift
        
        newPhase.onEnter(boss);
        syncMount(boss, comp, newPhase);
        syncWeapon(boss, newPhase);
    }

    public static void syncMount(LivingEntity boss, BossComponent comp, Phase phase) {
        if (boss.level().isClientSide()) return;

        String desiredMountId = phase.getMountEntity();
        net.minecraft.world.entity.Entity currentMount = comp.getCurrentMount();

        // Check if we already have the correct mount
        if (desiredMountId == null) {
            if (currentMount != null) {
                boss.stopRiding();
                currentMount.discard();
                comp.setCurrentMount(null);
            }
            return;
        }

        // We want a mount
        ResourceLocation mountLoc = ResourceLocation.parse(desiredMountId);
        if (currentMount != null) {
            if (net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(currentMount.getType()).equals(mountLoc)) {
                // Already riding the correct type of entity
                if (!boss.isPassengerOfSameVehicle(currentMount)) {
                    boss.startRiding(currentMount, true);
                }
                return;
            } else {
                // Riding wrong entity type
                boss.stopRiding();
                currentMount.discard();
                comp.setCurrentMount(null);
            }
        }

        // Spawn new mount
        net.minecraft.world.entity.EntityType<?> type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(mountLoc);
        if (type != null) {
            net.minecraft.world.entity.Entity newMount = type.create(boss.level());
            if (newMount != null) {
                newMount.setPos(boss.getX(), boss.getY(), boss.getZ());
                boss.level().addFreshEntity(newMount);
                boss.startRiding(newMount, true);
                comp.setCurrentMount(newMount);
            }
        }
    }

    public static void syncWeapon(LivingEntity boss, Phase phase) {
        if (boss.level().isClientSide()) return;
        
        String weaponId = phase.getMainhandWeapon();
        if (weaponId == null || weaponId.isEmpty()) {
            boss.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
            return;
        }

        ResourceLocation itemLoc = ResourceLocation.parse(weaponId);
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemLoc);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            boss.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(item));
        } else {
            boss.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        
        // Check if the entity is a mount for a boss
        if (entity.getFirstPassenger() instanceof LivingEntity rider && isBoss(rider)) {
            event.setCanceled(true);
            rider.hurt(event.getSource(), event.getAmount());
            return;
        }

        BossComponent comp = get(entity);
        if (comp != null && comp.getCurrentSequence() != null && comp.getCurrentSequence().isRunning()) {
            boolean isMelee = !event.getSource().isIndirect();
            comp.getCurrentSequence().onDamage(event, isMelee);
        }
    }


    @SubscribeEvent
    public static void onPoiseBroken(EntityEvents.PoiseBroken event) {
        BossComponent comp = get(event.getEntity());
        if (comp != null) {
            if (comp.getCurrentSequence() != null) {
                comp.setCurrentSequence(null); // Interrupt sequence
            }
            comp.setExhaustionTicks(comp.getExhaustionTicks() + 100); // stunned
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity living) {
            BossSkillData data = BossSkillData.get(living);
            if (data != null) {
                NetworkManager.sendToTracking(
                    new EntitySkillSyncPacket(
                        new SkillIndicatorData(
                            living.getId(), data.stepId, data.tickInStep, data.totalDuration,
                            data.counterType, data.counterWindowStart, data.counterWindowEnd,
                            data.counterDirection, data.magicElement, data.isParry
                        ), false
                    ),
                    living
                );
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living && isBoss(living)) {
            BossComponent comp = get(living);
            if (comp != null && comp.getCurrentMount() != null) {
                living.stopRiding();
                comp.getCurrentMount().discard();
                comp.setCurrentMount(null);
            }
        }
    }
}
