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
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
        entity.getPersistentData().putString("bdv2_boss_id", component.getDefinition().getId());
        initializeBossAttributes(entity, component.getDefinition());
        applyScale(entity, component.getDefinition().getBaseScale());
        component.initialize(entity);
        double maxVitality = component.getVitalityPool().getMaxVitality();
        float scaledPoise = com.nhatbh.basedefensev2.api.PoiseAPI.calculateMobMaxPoise((float) maxVitality);
        float reduction = (component.getDefinition() != null) ? component.getDefinition().getPoiseDamageReduction() : 0.95f;
        com.nhatbh.basedefensev2.api.PoiseAPI.initializePoise(entity, scaledPoise, reduction, true);

        if (component.getCurrentPhase() != null) {
            syncMount(entity, component, component.getCurrentPhase());
            syncWeapon(entity, component.getCurrentPhase());
            syncArmor(entity, component.getCurrentPhase());
        }
        syncBossVitality(entity, component);
    }

    public static void syncBossVitality(LivingEntity boss) {
        BossComponent comp = get(boss);
        if (comp != null) {
            syncBossVitality(boss, comp);
        }
    }

    public static void syncBossVitality(LivingEntity boss, BossComponent comp) {
        if (boss == null || boss.level().isClientSide() || comp == null) return;
        String bossId = comp.getDefinition() != null ? comp.getDefinition().getId() : boss.getPersistentData().getString("bdv2_boss_id");
        NetworkManager.sendToTracking(
                new com.nhatbh.basedefensev2.boss.network.BossVitalitySyncPacket(
                        boss.getId(),
                        bossId,
                        comp.getVitalityPool().getCurrentVitality(),
                        comp.getVitalityPool().getMaxVitality(),
                        comp.getCorrosionHits(),
                        comp.getCurrentPhaseIndex()
                ),
                boss
        );
    }

    public static void initializeBossAttributes(LivingEntity boss, BossDefinition def) {
        if (def.getBaseStats() != null) {
            var atts = boss.getAttributes();
            var speedAttr = atts.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(def.getBaseStats().speed);
            }
            var damageAttr = atts.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.setBaseValue(def.getBaseStats().damage);
            }
            var kbAttr = atts.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
            if (kbAttr != null) {
                kbAttr.setBaseValue(def.getBaseStats().knockbackResistance);
            }
            var followAttr = atts.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            if (followAttr != null) {
                followAttr.setBaseValue(300.0);
            }
        }
    }

    public static BossComponent get(LivingEntity entity) {
        BossComponent comp = BOSS_REGISTRY.get(entity);
        if (comp == null && entity != null && entity.getPersistentData().contains("bdv2_boss_id")) {
            String bossId = entity.getPersistentData().getString("bdv2_boss_id");
            BossDefinition def = com.nhatbh.basedefensev2.registry.ModBosses.get(bossId);
            if (def != null) {
                comp = new BossComponent(def);
                comp.initialize(entity);
                if (entity.getPersistentData().contains("BossPhaseIndex")) {
                    int phaseIndex = entity.getPersistentData().getInt("BossPhaseIndex");
                    comp.setCurrentPhaseIndex(phaseIndex);
                    if (phaseIndex >= 0 && phaseIndex < def.getPhases().size()) {
                        comp.setCurrentPhase(def.getPhases().get(phaseIndex));
                    }
                } else if (!def.getPhases().isEmpty()) {
                    comp.setCurrentPhase(def.getPhases().get(0));
                }
                BOSS_REGISTRY.put(entity, comp);
            }
        }
        return comp;
    }

    public static boolean isBoss(LivingEntity entity) {
        if (entity == null)
            return false;
        return BOSS_REGISTRY.containsKey(entity) || entity.getPersistentData().contains("bdv2_boss_id");
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity boss = event.getEntity();
        if (boss.level().isClientSide())
            return;

        BossComponent comp = get(boss);
        if (comp == null)
            return;

        // Sync boss NBT & component state to all tracking clients at boss tick
        comp.getVitalityPool().saveToNBT(boss.getPersistentData());
        comp.getAdaptiveArmorTracker().saveToNBT(boss.getPersistentData());
        boss.getPersistentData().putInt("BossPhaseIndex", comp.getCurrentPhaseIndex());
        boss.getPersistentData().putInt("BossCorrosionHits", comp.getCorrosionHits());
        syncBossVitality(boss, comp);

        // ── 1-Minute Inactivity Teleport Check ──
        long currentTime = boss.level().getGameTime();
        if (!boss.getPersistentData().contains("LastCombatGameTime")) {
            boss.getPersistentData().putLong("LastCombatGameTime", currentTime);
        }
        long lastCombat = boss.getPersistentData().getLong("LastCombatGameTime");
        long ticksSinceCombat = currentTime - lastCombat;

        if (ticksSinceCombat >= 1200) { // 1 minute without damage dealt to or by boss
            recordBossCombatActivity(boss);
            teleportInactiveBoss(boss);
        }

        checkPhaseTransition(boss, comp);

        if (comp.getExhaustionTicks() > 0) {
            comp.setExhaustionTicks(comp.getExhaustionTicks() - 1);
            return;
        }

        // Tick skill cooldowns
        for (Map.Entry<String, Integer> entry : comp.getSkillCooldowns().entrySet()) {
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - 1);
            }
        }

        Phase phase = comp.getCurrentPhase();
        if (phase != null) {
            if (!com.nhatbh.basedefensev2.api.PoiseAPI.isExhausted(boss)) {
                phase.tickPassives(boss);
            }
            comp.tickGlobalCooldowns();
        }

        // Stun Immunity & Strength-based Skill Blocking
        com.nhatbh.basedefensev2.strength.EntityStrengthData strengthData = com.nhatbh.basedefensev2.strength.EntityStrengthData
                .get(boss);
        if (strengthData != null && strengthData.currentStrength > 0) {
            net.minecraft.world.effect.MobEffect stunImmunity = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS
                    .getValue(net.minecraft.resources.ResourceLocation.parse("efn:sin_stun_immunity"));
            if (stunImmunity != null) {
                boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(stunImmunity, 3, 19, false, false));
            }
        }

        boolean sequenceJustEnded = false;
        if (comp.getCurrentSequence() != null) {
            if (comp.getCurrentSequence().isRunning()) {
                comp.getCurrentSequence().tick(boss);
                if (!comp.getCurrentSequence().isRunning()) {
                    sequenceJustEnded = true;
                }
            } else {
                sequenceJustEnded = true;
            }
        }

        if (sequenceJustEnded) {
            comp.setCurrentSequence(null);
            selectRandomTarget(boss);
        }

        if (boss instanceof net.minecraft.world.entity.Mob mob) {
            if (mob.getTarget() != null
                    && !com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.isValidTarget(mob.getTarget())) {
                selectRandomTarget(boss);
            }
        }

        if (comp.getCurrentSequence() == null) {
            // Only select new skills if not exhausted AND has strength
            if (comp.getExhaustionTicks() <= 0 && (strengthData == null || strengthData.currentStrength > 0)) {
                ActiveSkill nextSkill = SkillEvaluator.selectSkill(comp, boss, phase);
                if (nextSkill != null) {
                    comp.setSkillCooldown(nextSkill.getId(), nextSkill.getCooldown());
                    comp.markSkillUsed(nextSkill.getId(), nextSkill.getType());

                    // Check if all skills of this type are used, then reset
                    java.util.Set<String> used = (nextSkill.getType() == ActiveSkill.Type.BASIC)
                            ? comp.getUsedBasicSkills()
                            : comp.getUsedTacticalSkills();

                    long totalOfType = phase.getActives().stream()
                            .filter(e -> e.skill.getType() == nextSkill.getType())
                            .count();

                    if (used.size() >= totalOfType) {
                        comp.clearUsedSkills(nextSkill.getType());
                    }

                    // Set global cooldowns based on type and skill override
                    if (nextSkill.getType() == ActiveSkill.Type.BASIC) {
                        comp.setBasicSkillCooldown(nextSkill.getGlobalCooldown());
                    } else if (nextSkill.getType() == ActiveSkill.Type.TACTICAL) {
                        comp.setTacticalSkillCooldown(nextSkill.getGlobalCooldown());
                    }

                    comp.setCurrentSequence(nextSkill.getSequence().start(boss, nextSkill.getType()));
                }
            }
        }
    }

    public static void recordBossCombatActivity(LivingEntity boss) {
        if (boss == null || boss.level().isClientSide()) return;
        boss.getPersistentData().putLong("LastCombatGameTime", boss.level().getGameTime());
    }

    public static void teleportInactiveBoss(LivingEntity boss) {
        if (boss == null || boss.level().isClientSide()) return;
        if (!(boss.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        net.minecraft.server.level.ServerPlayer closestPlayer = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (net.minecraft.world.entity.player.Player p : serverLevel.players()) {
            if (p instanceof net.minecraft.server.level.ServerPlayer player) {
                if (com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.isValidTarget(player)) {
                    double dSq = boss.distanceToSqr(player);
                    if (dSq < minDistanceSq) {
                        minDistanceSq = dSq;
                        closestPlayer = player;
                    }
                }
            }
        }

        double targetX, targetY, targetZ;

        if (closestPlayer != null) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2;
            double distance = 2.0;
            targetX = closestPlayer.getX() + Math.cos(angle) * distance;
            targetZ = closestPlayer.getZ() + Math.sin(angle) * distance;
            targetY = closestPlayer.getY();
        } else {
            // Fallback to arena center
            com.nhatbh.basedefensev2.stage.core.StageContext ctx = com.nhatbh.basedefensev2.stage.core.StageContext.getOrCreate(serverLevel);
            if (ctx != null && ctx.getSpawnArea() != null) {
                com.nhatbh.basedefensev2.stage.config.StageConfig.SpawnArea spawnArea = ctx.getSpawnArea();
                targetX = spawnArea.x;
                targetY = spawnArea.y;
                targetZ = spawnArea.z;
            } else {
                net.minecraft.core.BlockPos spawnPos = serverLevel.getSharedSpawnPos();
                targetX = spawnPos.getX() + 0.5;
                targetY = spawnPos.getY();
                targetZ = spawnPos.getZ() + 0.5;
            }
        }

        // Spawn FX at old position
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, boss.getX(), boss.getY() + 1.0,
                boss.getZ(), 40, 0.5, 1.0, 0.5, 0.2);
        serverLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f);

        boss.teleportTo(targetX, targetY, targetZ);

        // Spawn FX at new position
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, targetX, targetY + 1.0, targetZ,
                40, 0.5, 1.0, 0.5, 0.2);
        serverLevel.playSound(null, targetX, targetY, targetZ, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f);

        if (boss instanceof Mob mob) {
            mob.setTarget(closestPlayer);
        }
    }

    public static void teleportBossToRandomPlayer(LivingEntity boss) {
        teleportInactiveBoss(boss);
    }

    public static double calculateBossArmor(LivingEntity boss) {
        int level = com.nhatbh.basedefensev2.level.MobLevelData.getLevel(boss);
        double baseArmor = 10.0;
        if (boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) != null) {
            baseArmor = boss.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            if (baseArmor <= 0) baseArmor = 10.0;
        }
        double levelBonus = 2.0 * (level - 1);
        if (level > 70) {
            double extra = level - 70;
            levelBonus += 0.5 * Math.pow(extra, 1.5);
        }
        return baseArmor + levelBonus;
    }

    public static double calculateApotheosisMultiplier(double effectiveArmor) {
        if (effectiveArmor <= 0) return 1.0;
        return 50.0 / (50.0 + effectiveArmor);
    }

    public static void checkPhaseTransition(LivingEntity boss, BossComponent comp) {
        BossDefinition def = comp.getDefinition();
        if (def.getPhases().isEmpty())
            return;

        double hpPercent = comp.getVitalityPool().getRatio();

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
        comp.setExhaustionTicks(0);

        selectRandomTarget(boss);

        newPhase.onEnter(boss);
        syncMount(boss, comp, newPhase);
        syncWeapon(boss, newPhase);
        syncArmor(boss, newPhase);

        // Instant Strength & Poise Recovery to recover instantly on phase transition and prevent phase skipping
        com.nhatbh.basedefensev2.api.PoiseAPI.resetPoise(boss);
        syncBossVitality(boss, comp);
    }

    public static void syncMount(LivingEntity boss, BossComponent comp, Phase phase) {
        if (boss.level().isClientSide())
            return;

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
            if (net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(currentMount.getType())
                    .equals(mountLoc)) {
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
        net.minecraft.world.entity.EntityType<?> type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getValue(mountLoc);
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
        if (boss.level().isClientSide())
            return;

        String weaponId = phase.getMainhandWeapon();
        String nbtStr = phase.getMainhandNbt();

        if (weaponId == null || weaponId.isEmpty()) {
            boss.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    net.minecraft.world.item.ItemStack.EMPTY);
            return;
        }

        ResourceLocation itemLoc = ResourceLocation.parse(weaponId);
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemLoc);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
            if (nbtStr != null && !nbtStr.isEmpty()) {
                try {
                    stack.setTag(net.minecraft.nbt.TagParser.parseTag(nbtStr));
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    // Silently fail or log for debug
                }
            }
            makeUnbreakable(stack);
            boss.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack);
        } else {
            boss.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    public static void syncArmor(LivingEntity boss, Phase phase) {
        if (boss.level().isClientSide())
            return;

        equipItem(boss, net.minecraft.world.entity.EquipmentSlot.HEAD, phase.getHelmet());
        equipItem(boss, net.minecraft.world.entity.EquipmentSlot.CHEST, phase.getChestplate());
        equipItem(boss, net.minecraft.world.entity.EquipmentSlot.LEGS, phase.getLeggings());
        equipItem(boss, net.minecraft.world.entity.EquipmentSlot.FEET, phase.getBoots());
    }

    private static void equipItem(LivingEntity entity, net.minecraft.world.entity.EquipmentSlot slot, String itemId) {
        if (itemId == null || itemId.isEmpty())
            return;

        ResourceLocation loc = ResourceLocation.parse(itemId);
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(loc);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
            makeUnbreakable(stack);
            entity.setItemSlot(slot, stack);
        }
    }

    private static void makeUnbreakable(net.minecraft.world.item.ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.getOrCreateTag().putBoolean("Unbreakable", true);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onSpellDamage(io.redspace.ironsspellbooks.api.events.SpellDamageEvent event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide)
            return;
        BossComponent comp = get(event.getEntity());
        if (comp != null) {
            // Store raw damage for counter logic
            event.getEntity().getPersistentData().putFloat("MagicCounterRawDamage", event.getAmount());
        }
    }

    public static void debugChat(LivingEntity entity, String message) {
        if (entity == null || entity.level().isClientSide()) return;
        for (net.minecraft.world.entity.player.Player p : entity.level().players()) {
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onLivingAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity != null && !entity.level().isClientSide()) {
            if (isBoss(entity)) {
                recordBossCombatActivity(entity);
            }
            if (event.getSource().getEntity() instanceof LivingEntity attacker && isBoss(attacker)) {
                recordBossCombatActivity(attacker);
            }
        }
        if (entity != null && !entity.level().isClientSide() && isBoss(entity)) {
            BossComponent comp = get(entity);
            if (comp != null && event.getAmount() > 0) {
                net.minecraft.world.damagesource.DamageSource source = event.getSource();
                boolean isMelee = source.getDirectEntity() != null && source.getDirectEntity() == source.getEntity();
                if (isMelee) {
                    comp.incrementCorrosionHits();
                }

                double armor = calculateBossArmor(entity);
                double effectiveArmor = armor * comp.getCorrosionMultiplier(armor);
                double apotheosisMult = calculateApotheosisMultiplier(effectiveArmor);
                double rawAmount = event.getAmount();
                double preCalculatedMitigated = rawAmount * apotheosisMult;

                entity.getPersistentData().putDouble("bdv2_cached_pre_mitigated_dmg", preCalculatedMitigated);
                entity.getPersistentData().putDouble("bdv2_cached_raw_dmg", rawAmount);
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onBossHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide())
            return;

        if (!isBoss(entity))
            return;

        if (event.isCanceled() || event.getAmount() <= 0)
            return;

        float currentHp = entity.getHealth();
        float maxHp = entity.getMaxHealth();

        float missingHp = maxHp - currentHp;
        if (missingHp <= 0) {
            event.setCanceled(true);
            return;
        }

        float maxAllowedTotalHeal = maxHp * 0.25f;
        float currentTotalHealed = entity.getPersistentData().getFloat("bdv2_total_healed");
        float remainingBudget = maxAllowedTotalHeal - currentTotalHealed;

        if (remainingBudget <= 0) {
            event.setCanceled(true);
            return;
        }

        float requestedHeal = event.getAmount();
        float effectiveHeal = Math.min(requestedHeal, missingHp);
        float allowedHeal = Math.min(effectiveHeal, remainingBudget);

        if (allowedHeal <= 0) {
            event.setCanceled(true);
        } else {
            event.setAmount(allowedHeal);
            entity.getPersistentData().putFloat("bdv2_total_healed", currentTotalHealed + allowedHeal);
            BossComponent comp = get(entity);
            if (comp != null) {
                comp.getVitalityPool().heal(allowedHeal);
                comp.getVitalityPool().saveToNBT(entity.getPersistentData());
                comp.getVitalityPool().syncToVanillaHealth(entity);
                syncBossVitality(entity, comp);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity != null && !entity.level().isClientSide()) {
            if (isBoss(entity)) {
                recordBossCombatActivity(entity);
            }
            if (event.getSource().getEntity() instanceof LivingEntity attacker && isBoss(attacker)) {
                recordBossCombatActivity(attacker);
            }
        }

        // Check if the entity is a mount for a boss
        if (entity.getFirstPassenger() instanceof LivingEntity rider && isBoss(rider)) {
            event.setCanceled(true);
            rider.hurt(event.getSource(), event.getAmount());
            return;
        }

        BossComponent comp = get(entity);
        if (comp != null && comp.getCurrentSequence() != null && comp.getCurrentSequence().isRunning()) {
            net.minecraft.world.damagesource.DamageSource source = event.getSource();
            // Strict melee check: source has an attacker and the direct entity that hit is
            // the attacker himself
            boolean isMelee = source.getDirectEntity() != null && source.getDirectEntity() == source.getEntity();

            float rawDamage = event.getAmount();
            if (entity.getPersistentData().contains("MagicCounterRawDamage")) {
                rawDamage = entity.getPersistentData().getFloat("MagicCounterRawDamage");
                entity.getPersistentData().remove("MagicCounterRawDamage");
            }

            comp.getCurrentSequence().onDamage(event, isMelee, rawDamage);
        }
    }

    @SubscribeEvent
    public static void onGuard(com.complextalents.epicfight.event.EpicFightGuardEvent event) {
        if (event.getAttacker() == null || event.getAttacker().level().isClientSide)
            return;

        if (event.isParry() && isBoss(event.getAttacker()) && event.getPlayer() != null) {
            com.nhatbh.basedefensev2.effects.RiposteEffect.applyTo(event.getPlayer());
        }

        BossComponent comp = get(event.getAttacker());
        if (comp != null && comp.getCurrentSequence() != null && comp.getCurrentSequence().isRunning()) {
            comp.getCurrentSequence().onGuard(event);
        }
    }

    @SubscribeEvent
    public static void onPoiseBroken(EntityEvents.PoiseBroken event) {
        BossComponent comp = get(event.getEntity());
        if (comp != null) {
            if (comp.getCurrentSequence() != null) {
                comp.setCurrentSequence(null); // Interrupt sequence
            }
            comp.setExhaustionTicks(300); // 15 seconds stunned during poise recovery
            selectRandomTarget(event.getEntity());
        }
        if (event.getEntity() instanceof Mob mob && isBoss(mob)) {
            mob.getNavigation().stop();
            if (!mob.onGround()) {
                mob.setDeltaMovement(0, -0.6, 0);
                mob.hasImpulse = true;
            } else {
                mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
            }
            if (mob.isNoAi() && mob.getPersistentData().getBoolean("ExhaustionDisabledAI")) {
                mob.setNoAi(false);
                mob.getPersistentData().remove("ExhaustionDisabledAI");
            }
        }
    }

    public static void selectRandomTarget(LivingEntity boss) {
        if (boss == null || boss.level().isClientSide())
            return;

        double range = 100.0;
        var followAttr = boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        if (followAttr != null && followAttr.getValue() > 0) {
            range = followAttr.getValue();
        }

        double maxDistSqr = range * range;
        java.util.List<net.minecraft.server.level.ServerPlayer> validPlayers = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.player.Player p : boss.level().players()) {
            if (p instanceof net.minecraft.server.level.ServerPlayer player) {
                if (com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.isValidTarget(player)
                        && boss.distanceToSqr(player) <= maxDistSqr) {
                    validPlayers.add(player);
                }
            }
        }

        if (boss instanceof net.minecraft.world.entity.Mob mob) {
            if (!validPlayers.isEmpty()) {
                net.minecraft.server.level.ServerPlayer randomTarget = validPlayers
                        .get(boss.getRandom().nextInt(validPlayers.size()));
                mob.setTarget(randomTarget);
            } else {
                mob.setTarget(null);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(net.minecraftforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide())
            return;
        net.minecraft.world.entity.LivingEntity newTarget = event.getNewTarget();
        if (newTarget != null
                && !com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.isValidTarget(newTarget)) {
            event.setCanceled(true);
            event.setNewTarget(null);
        }
    }

    @SubscribeEvent
    public static void onMobTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide())
            return;
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob mob && !isBoss(mob)) {
            net.minecraft.world.entity.LivingEntity target = mob.getTarget();
            if (target != null && !com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.isValidTarget(target)) {
                mob.setTarget(null);
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity living) {
            if (isBoss(living)) {
                syncBossVitality(living);
            }
            BossSkillData data = BossSkillData.get(living);
            if (data != null) {
                NetworkManager.sendToTracking(
                        new EntitySkillSyncPacket(
                                new SkillIndicatorData(
                                        living.getId(), data.stepId, data.tickInStep, data.totalDuration,
                                        data.counterType, data.counterWindowStart, data.counterWindowEnd,
                                        data.counterDirection, data.magicElement, data.isParry),
                                false),
                        living);
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

    public static void applyScale(LivingEntity boss, float scale) {
        if (boss.level().isClientSide() || boss.getServer() == null)
            return;

        applyRawScale(boss, scale);
    }

    private static void applyRawScale(net.minecraft.world.entity.Entity entity, float scale) {
        if (entity.level().isClientSide() || entity.getServer() == null)
            return;

        String command = String.format("scale set %.2f %s", scale, entity.getStringUUID());
        entity.getServer().getCommands().performPrefixedCommand(
                entity.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                command);
    }
}
