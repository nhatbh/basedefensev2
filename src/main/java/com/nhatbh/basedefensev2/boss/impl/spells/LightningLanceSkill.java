package com.nhatbh.basedefensev2.boss.impl.spells;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Lightning Lance Barrage Skill (Lôi Thương)
 * 
 * Target: TARGET_LOWEST_HP
 * Mechanics: Fires a barrage of Lightning Lances at the lowest HP target's area
 * with spread accuracy,
 * creating a lethal rain of lances that players can dodge.
 * 
 * Damage: Hybrid 15% Max HP total (7.5% Max HP + flat damage equal to 7.5% of
 * 30 HP = 2.25 flat).
 */
public class LightningLanceSkill {

    private static final String SPELL_ID = "irons_spellbooks:lightning_lance";
    private static final Random RNG = new Random();

    // Damage calculations per lance:
    // 0 Flat Damage + 1% Max HP (0.01) for testing
    private static final float FLAT_DMG_PER_LANCE = 0.0f;
    private static final float MAX_HP_PCT_PER_LANCE = 0.01f;

    public static ActiveSequence create() {
        return ActiveSequence.builder("lightning_lance")
                // Phase 1: Windup & Target Lock (10 ticks)
                .step("windup", 10)
                .onStart(ctx -> {
                    LivingEntity boss = ctx.boss();
                    Player target = TargetUtils.getLowestHpPlayer(boss, 32.0);
                    if (target != null) {
                        ctx.data().put("target_pos", target.position());
                    } else {
                        ctx.data().put("target_pos", boss.position().add(boss.getLookAngle().scale(10.0)));
                    }
                })

                // Phase 2: Barrage Fire (40 ticks — fires every 4 ticks = 10 lances total)
                .step("barrage", 40)
                .onTick(ctx -> {
                    int tick = ctx.getTicks();
                    if (tick % 4 != 0)
                        return; // Fire every 4 ticks

                    LivingEntity boss = ctx.boss();
                    Vec3 baseTargetPos = (Vec3) ctx.data().get("target_pos");
                    if (baseTargetPos == null)
                        baseTargetPos = boss.position();

                    // Apply low percentage accuracy spread (random offset within 3.5 block radius)
                    double offsetX = (RNG.nextDouble() - 0.5) * 7.0;
                    double offsetZ = (RNG.nextDouble() - 0.5) * 7.0;
                    Vec3 impactPos = baseTargetPos.add(offsetX, 0, offsetZ);

                    // Origin position: Midpoint between boss and impact target, elevated 14 blocks
                    // up in the sky for an angled rain trajectory
                    Vec3 bossEye = boss.getEyePosition();
                    Vec3 midPoint = bossEye.add(impactPos).scale(0.5);
                    Vec3 originPos = midPoint.add(0, 14.0, 0);

                    // Cast Lightning Lance raining down at an angle from originPos to impactPos
                    BossSpellCaster.castSpellFromLocation(boss, originPos, impactPos, SPELL_ID, FLAT_DMG_PER_LANCE,
                            MAX_HP_PCT_PER_LANCE);
                })

                // Phase 3: Recovery (10 ticks)
                .step("recovery", 10)
                .build();
    }
}
