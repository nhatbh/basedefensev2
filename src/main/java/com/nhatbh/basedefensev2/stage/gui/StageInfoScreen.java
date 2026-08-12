package com.nhatbh.basedefensev2.stage.gui;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.registry.ModBosses;
import com.nhatbh.basedefensev2.stage.config.MobSpawnEntry;
import com.nhatbh.basedefensev2.stage.config.StageConfig;
import com.nhatbh.basedefensev2.stage.config.WaveConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class StageInfoScreen extends Screen {

    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 350;

    private final List<StageConfig> stages = new ArrayList<>();
    private int currentStageIndex = 0;
    private int currentStageNumberToRun = 1;

    private LivingEntity currentBossEntity = null;
    private String currentBossEntityId = "";

    // Interactive Orb bounding boxes for mouse clicks
    private static class OrbClickZone {
        int index;
        int x, y, r;

        OrbClickZone(int index, int x, int y, int r) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.r = r;
        }

        boolean contains(double mouseX, double mouseY) {
            double dx = mouseX - x;
            double dy = mouseY - y;
            return (dx * dx + dy * dy) <= (r * r);
        }
    }

    private final List<OrbClickZone> orbClickZones = new ArrayList<>();

    public StageInfoScreen(List<StageConfig> allStages, int activeStageNum, boolean seeAll) {
        super(Component.literal("Stage Timeline & Boss Intelligence"));
        this.stages.addAll(allStages);
        this.stages.sort(Comparator.comparingInt(s -> s.order));
        this.currentStageNumberToRun = activeStageNum;

        // Default focus on active stage
        this.currentStageIndex = Math.max(0, Math.min(activeStageNum - 1, stages.size() - 1));
    }

    @Override
    protected void init() {
        super.init();

        int cardX = (this.width - PANEL_WIDTH) / 2;
        int cardY = (this.height - PANEL_HEIGHT) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(cardX + (PANEL_WIDTH - 90) / 2, cardY + PANEL_HEIGHT - 26, 90, 18)
                .build());

        updateBoss3DEntity();
    }

    private void updateBoss3DEntity() {
        if (stages.isEmpty() || minecraft == null || minecraft.level == null) return;
        StageConfig stage = stages.get(currentStageIndex);

        MobSpawnEntry bossEntry = null;
        for (WaveConfig w : stage.waves) {
            for (MobSpawnEntry m : w.mobs) {
                if (m.is_boss && !m.boss_id.contains("miniboss")) {
                    bossEntry = m;
                    break;
                }
            }
        }

        if (bossEntry != null) {
            BossDefinition bossDef = getBossDef(bossEntry.boss_id);
            if (bossDef != null && bossDef.getBaseEntity() != null) {
                String baseEntity = bossDef.getBaseEntity();
                if (!baseEntity.equals(currentBossEntityId)) {
                    currentBossEntityId = baseEntity;
                    EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(baseEntity));
                    if (type != null) {
                        Entity entity = type.create(minecraft.level);
                        if (entity instanceof LivingEntity living) {
                            currentBossEntity = living;
                        }
                    }
                }
                return;
            }
        }
        currentBossEntity = null;
        currentBossEntityId = "";
    }

    private BossDefinition getBossDef(String bossId) {
        BossDefinition bossDef = ModBosses.get(bossId);
        if (bossDef == null && (bossId.startsWith("gen_boss_stage_") || bossId.startsWith("gen_miniboss_stage_"))) {
            com.nhatbh.basedefensev2.stage.generator.RandomStageGenerator.registerBossesOnly(100L);
            bossDef = ModBosses.get(bossId);
        }
        return bossDef;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            for (OrbClickZone zone : orbClickZones) {
                if (zone.contains(mouseX, mouseY)) {
                    if (this.currentStageIndex != zone.index) {
                        this.currentStageIndex = zone.index;
                        updateBoss3DEntity();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g);

        int cardX = (this.width - PANEL_WIDTH) / 2;
        int cardY = (this.height - PANEL_HEIGHT) / 2;

        // Main Glassmorphic Container
        g.fill(cardX, cardY, cardX + PANEL_WIDTH, cardY + PANEL_HEIGHT, 0xF50A0C12);
        g.renderOutline(cardX, cardY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF664411);
        g.renderOutline(cardX + 1, cardY + 1, PANEL_WIDTH - 2, PANEL_HEIGHT - 2, 0xFF33220A);
        g.renderOutline(cardX + 3, cardY + 3, PANEL_WIDTH - 6, PANEL_HEIGHT - 6, 0xFF141C28);

        if (stages.isEmpty()) {
            g.drawCenteredString(this.font, "§cNo stage data available", this.width / 2, cardY + 160, 0xFFFFFF);
            super.render(g, mouseX, mouseY, partialTicks);
            return;
        }

        // Header Title Banner
        g.drawCenteredString(this.font, "★ STAGE TIMELINE & BOSS INTELLIGENCE ★", this.width / 2, cardY + 10, 0xFFFFAA00);
        g.hLine(cardX + 20, cardX + PANEL_WIDTH - 20, cardY + 22, 0xFF443311);

        // 1. TOP SECTION: Timeline Progress Bar with Orbs
        renderTimelineSection(g, cardX + 20, cardY + 30, PANEL_WIDTH - 40, 70, mouseX, mouseY);

        // 2. BOTTOM SECTION: Boss Information Card
        StageConfig selectedStage = stages.get(currentStageIndex);
        renderBossCardSection(g, cardX + 20, cardY + 108, PANEL_WIDTH - 40, 180, selectedStage, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTicks);
    }

    // ── Timeline & Stage Orbs ──────────────────────────────────────────────────
    private void renderTimelineSection(GuiGraphics g, int x, int y, int width, int height, int mouseX, int mouseY) {
        orbClickZones.clear();

        int totalStages = stages.size();
        if (totalStages == 0) return;

        int trackY = y + 32;
        int trackStartX = x + 24;
        int trackEndX = x + width - 24;
        int trackW = trackEndX - trackStartX;

        // Background Track Frame
        g.fill(trackStartX, trackY - 3, trackEndX, trackY + 3, 0xFF161E2E);
        g.renderOutline(trackStartX - 1, trackY - 4, trackW + 2, 8, 0xFF334466);

        // Solid Progress Bar up to defeated / active stage
        int defeatedStageIndex = Math.max(0, Math.min(currentStageNumberToRun - 1, totalStages - 1));
        float fillRatio = totalStages <= 1 ? 0.0f : (float) defeatedStageIndex / (totalStages - 1);
        int filledW = (int) (trackW * fillRatio);

        if (filledW > 0) {
            g.fill(trackStartX, trackY - 2, trackStartX + filledW, trackY + 2, 0xFF00AAFF);
        }

        // Render Stage Orbs
        for (int i = 0; i < totalStages; i++) {
            StageConfig stage = stages.get(i);
            float ratio = totalStages == 1 ? 0.5f : (float) i / (totalStages - 1);
            int orbX = trackStartX + (int) (trackW * ratio);
            int orbY = trackY;
            int radius = 10;

            boolean isSelected = (i == currentStageIndex);
            boolean isCurrentActive = (stage.order + 1 == currentStageNumberToRun);
            boolean isHovered = (mouseX - orbX) * (mouseX - orbX) + (mouseY - orbY) * (mouseY - orbY) <= radius * radius;

            orbClickZones.add(new OrbClickZone(i, orbX, orbY, radius));

            // Orb Outer Ring / Glow
            int ringColor = isSelected ? 0xFFFFAA00 : (isCurrentActive ? 0xFF00FF88 : (isHovered ? 0xFF66AAFF : 0xFF445577));
            int fillColor = isSelected ? 0xFF885500 : (isCurrentActive ? 0xFF006633 : 0xFF101622);

            g.fill(orbX - radius, orbY - radius, orbX + radius, orbY + radius, fillColor);
            g.renderOutline(orbX - radius, orbY - radius, radius * 2, radius * 2, ringColor);
            if (isSelected || isHovered) {
                g.renderOutline(orbX - radius - 1, orbY - radius - 1, radius * 2 + 2, radius * 2 + 2, 0xFFFFFFFF);
            }

            // Stage Number inside Orb
            String stageNumStr = String.valueOf(stage.order + 1);
            int textW = font.width(stageNumStr);
            int textColor = isSelected ? 0xFFFFFF : (isCurrentActive ? 0xAAFFBB : 0x88AABB);
            g.drawString(font, stageNumStr, orbX - (textW / 2), orbY - 4, textColor, true);

            // Subtitle under Orb: Element Affinity
            ElementType elem = com.nhatbh.basedefensev2.api.StageAPI.getStageElement(stage);
            String elemName = elem != null ? elem.name() : "NONE";
            int elemColor = com.nhatbh.basedefensev2.api.StageAPI.getElementColor(elem);

            g.pose().pushPose();
            g.pose().scale(0.7f, 0.7f, 1.0f);
            int scaledX = (int) (orbX / 0.7f);
            int scaledY = (int) ((orbY + 13) / 0.7f);
            g.drawCenteredString(font, elemName, scaledX, scaledY, elemColor);
            g.pose().popPose();
        }
    }

    // ── Boss Info Card ────────────────────────────────────────────────────────
    private void renderBossCardSection(GuiGraphics g, int x, int y, int width, int height, StageConfig stage, int mouseX, int mouseY) {
        g.fill(x, y, x + width, y + height, 0xFF121622);
        g.renderOutline(x, y, width, height, 0xFF775522);
        g.renderOutline(x + 1, y + 1, width - 2, height - 2, 0xFF332211);

        MobSpawnEntry bossEntry = null;
        for (WaveConfig w : stage.waves) {
            for (MobSpawnEntry m : w.mobs) {
                if (m.is_boss && !m.boss_id.contains("miniboss")) {
                    bossEntry = m;
                    break;
                }
            }
        }

        if (bossEntry == null) {
            g.drawCenteredString(font, "§7No major boss encounter configured for Stage " + (stage.order + 1), x + width / 2, y + height / 2 - 4, 0xAAAAAA);
            return;
        }

        BossDefinition bossDef = getBossDef(bossEntry.boss_id);
        if (bossDef == null) {
            g.drawCenteredString(font, "§cUnknown Boss Definition: " + bossEntry.boss_id, x + width / 2, y + height / 2 - 4, 0xFF5555);
            return;
        }

        // 1. Left Side: 3D Model Display Box
        int modelBoxX = x + 8;
        int modelBoxY = y + 8;
        int modelBoxW = 120;
        int modelBoxH = height - 16;

        g.fill(modelBoxX, modelBoxY, modelBoxX + modelBoxW, modelBoxY + modelBoxH, 0xFF080B12);
        g.renderOutline(modelBoxX, modelBoxY, modelBoxW, modelBoxH, 0xFF445577);

        if (currentBossEntity != null && minecraft != null) {
            float entityHeight = currentBossEntity.getBbHeight();
            int scale = 40;
            if (entityHeight > 0) {
                scale = (int) Math.max(16, Math.min(55, 55 / Math.max(1.0f, entityHeight)));
            }
            int renderCenterX = modelBoxX + (modelBoxW / 2);
            int renderCenterY = modelBoxY + modelBoxH - 12;

            long time = minecraft.level != null ? minecraft.level.getGameTime() : 0;
            float rotationAngle = (time % 360) * 2.5f;
            float xAngle = (float) Math.sin(Math.toRadians(rotationAngle)) * 0.8f;
            float yAngle = 0.0f;

            InventoryScreen.renderEntityInInventoryFollowsAngle(g, renderCenterX, renderCenterY, scale, xAngle, yAngle, currentBossEntity);
        }

        // 2. Right Side: Full Boss Stats & Skill Breakdown
        int infoX = modelBoxX + modelBoxW + 12;
        int infoY = y + 8;
        int infoW = width - modelBoxW - 24;

        String titlePrefix = "";
        String passiveName = "None";
        String passiveDesc = "Grants unique combat mechanics.";
        if (!bossDef.getPhases().isEmpty() && !bossDef.getPhases().get(0).getPassives().isEmpty()) {
            var passive = bossDef.getPhases().get(0).getPassives().get(0);
            titlePrefix = passive.getTitlePrefix();
            passiveName = passive.getName();
            passiveDesc = passive.getDescription();
        }

        String rawEntity = bossDef.getBaseEntity();
        String mobName = rawEntity.contains(":") ? rawEntity.split(":")[1] : rawEntity;
        mobName = com.nhatbh.basedefensev2.api.StageAPI.formatSkillName(mobName);

        String bossDisplayName = (!titlePrefix.isEmpty() ? titlePrefix + " " : "") + mobName;

        // Boss Header Title
        g.drawString(font, "§f§l" + bossDisplayName.toUpperCase(), infoX, infoY, 0xFFFFFF);
        infoY += 11;

        // Health & Poise Stat Bars
        int hpVal = (int) bossDef.getBaseStats().health;
        int poiseVal = (int) bossDef.getMaxPoise();

        g.drawString(font, "§cHP: §f" + hpVal + "   §ePoise: §f" + poiseVal, infoX, infoY, 0xFFFFFF);
        infoY += 9;

        int miniBarW = (infoW - 8) / 2;
        g.fill(infoX, infoY, infoX + miniBarW, infoY + 3, 0xFF441111);
        g.fill(infoX, infoY, infoX + miniBarW, infoY + 3, 0xFFDD2222);

        int poiseBarX = infoX + miniBarW + 8;
        g.fill(poiseBarX, infoY, poiseBarX + miniBarW, infoY + 3, 0xFF443300);
        g.fill(poiseBarX, infoY, poiseBarX + miniBarW, infoY + 3, 0xFFFFCC00);
        infoY += 8;

        // Passive Skill Card
        int passiveBoxH = 34;
        g.fill(infoX, infoY, infoX + infoW, infoY + passiveBoxH, 0xFF1B1626);
        g.renderOutline(infoX, infoY, infoW, passiveBoxH, 0xFF8855CC);
        g.drawString(font, "§d⚡ " + passiveName, infoX + 6, infoY + 3, 0xFFFF88);

        g.pose().pushPose();
        g.pose().scale(0.68f, 0.68f, 1.0f);
        int scaledDescX = (int) ((infoX + 6) / 0.68f);
        int scaledDescY = (int) ((infoY + 14) / 0.68f);
        int maxScaledWidth = (int) ((infoW - 12) / 0.68f);

        for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal("§7" + passiveDesc), maxScaledWidth)) {
            g.drawString(font, line, scaledDescX, scaledDescY, 0xDDDDDD);
            scaledDescY += 9;
        }
        g.pose().popPose();

        infoY += passiveBoxH + 4;

        // Active Skills Breakdown across all phases (De-duplicated base skills)
        List<com.nhatbh.basedefensev2.boss.skills.ActiveSkill> allActiveSkills = new ArrayList<>();
        java.util.Set<String> seenBaseSkillIds = new java.util.HashSet<>();

        for (var phase : bossDef.getPhases()) {
            for (var entry : phase.getActives()) {
                String baseId = entry.skill.getId().replaceAll("_p[0-9]+", "").replaceAll("_mb[0-9]+", "");
                if (seenBaseSkillIds.add(baseId)) {
                    allActiveSkills.add(entry.skill);
                }
            }
        }

        g.drawString(font, "§eACTIVE SKILLS §7(" + allActiveSkills.size() + ")", infoX, infoY, 0xFFFFAA00);
        infoY += 10;

        int skillColW = (infoW - 6) / 2;
        int cardH = 18;

        for (int i = 0; i < allActiveSkills.size() && i < 6; i++) {
            com.nhatbh.basedefensev2.boss.skills.ActiveSkill skill = allActiveSkills.get(i);
            int col = i % 2;
            int row = i / 2;

            int skX = infoX + col * (skillColW + 6);
            int skY = infoY + row * (cardH + 4);

            String skName = skill.getDisplayName();
            float cdSec = skill.getCooldown() / 20.0f;
            String cdStr = String.format("%.1fs CD", cdSec);

            g.fill(skX, skY, skX + skillColW, skY + cardH, 0xFF14202D);
            g.renderOutline(skX, skY, skillColW, cardH, 0xFF336688);

            // Skill Header (Name + Cooldown)
            g.drawString(font, "§a► " + skName, skX + 4, skY + 5, 0xFFFFFF);
            int cdWidth = font.width(cdStr);
            g.drawString(font, "§e" + cdStr, skX + skillColW - cdWidth - 4, skY + 5, 0xFFFFAA00);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────


    public static void openScreenWithData(List<StageConfig> stages, int currentStageNum) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (stages == null || stages.isEmpty()) {
            stages = new ArrayList<>();
            if (mc.getSingleplayerServer() != null) {
                net.minecraft.server.level.ServerLevel serverLevel = mc.getSingleplayerServer().overworld();
                var worldData = com.nhatbh.basedefensev2.stage.generator.WorldStageSavedData.get(serverLevel);
                stages.addAll(worldData.getAllStages());

                var ctx = com.nhatbh.basedefensev2.stage.core.StageContext.getOrCreate(serverLevel);
                if (ctx != null && ctx.getActiveConfig() != null) {
                    currentStageNum = ctx.getActiveConfig().order + 1;
                }
            }
        }

        mc.setScreen(new StageInfoScreen(stages, currentStageNum, true));
    }

    public static void openScreen() {
        openScreenWithData(new ArrayList<>(), 1);
    }
}
