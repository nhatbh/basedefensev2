package com.nhatbh.basedefensev2.classification;

import com.nhatbh.basedefensev2.boss.core.BossManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MobClassificationScreen extends Screen {

    private static final int CARD_WIDTH = 380;
    private static final int CARD_HEIGHT = 210;

    private final List<EntityType<? extends LivingEntity>> mobTypes = new ArrayList<>();
    private int currentIndex = 0;

    private LivingEntity currentEntity = null;
    private Checkbox rangedCheckbox = null;

    public MobClassificationScreen() {
        super(Component.literal("Hostile Entity Classification"));
    }

    @Override
    protected void init() {
        super.init();
        mobTypes.clear();

        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            if (type.getCategory() == MobCategory.MONSTER || isMonsterCandidate(type)) {
                @SuppressWarnings("unchecked")
                EntityType<? extends LivingEntity> livingType = (EntityType<? extends LivingEntity>) type;
                mobTypes.add(livingType);
            }
        }

        mobTypes.sort(Comparator.comparing(t -> ForgeRegistries.ENTITY_TYPES.getKey(t).toString()));

        if (mobTypes.isEmpty()) {
            return;
        }

        if (currentIndex >= mobTypes.size()) {
            currentIndex = 0;
        }

        updateCurrentEntity();
        buildClassificationButtons();
    }

    private boolean isMonsterCandidate(EntityType<?> type) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (key == null) return false;
        return !type.getCategory().isFriendly() && type.getCategory() != MobCategory.MISC;
    }

    private void updateCurrentEntity() {
        if (mobTypes.isEmpty()) {
            currentEntity = null;
            return;
        }

        EntityType<? extends LivingEntity> type = mobTypes.get(currentIndex);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity spawned = type.create(mc.level);
            if (spawned instanceof LivingEntity living) {
                currentEntity = living;
            } else {
                currentEntity = null;
            }
        }
    }

    private void buildClassificationButtons() {
        this.clearWidgets();

        int cardLeft = (this.width - CARD_WIDTH) / 2;
        int cardTop = (this.height - CARD_HEIGHT) / 2;

        // Header Row: [< Prev]  [Header Info]  [Next >]
        int headerY = cardTop + 10;
        this.addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> previousMob())
                .bounds(cardLeft + 12, headerY, 48, 16).build());

        this.addRenderableWidget(Button.builder(Component.literal("Next >"), b -> nextMob())
                .bounds(cardLeft + CARD_WIDTH - 60, headerY, 48, 16).build());

        // Single Bottom Control Row: [Exclude] | [Ranged] | [Monster] [Elites] [Miniboss] [Boss]
        int bottomRowY = cardTop + CARD_HEIGHT - 28;

        ResourceLocation key = mobTypes.isEmpty() ? null : ForgeRegistries.ENTITY_TYPES.getKey(mobTypes.get(currentIndex));
        String mobIdStr = key != null ? key.toString() : "";
        ClassificationManager.MobData data = ClassificationManager.getMobData(mobIdStr);

        boolean defaultRanged = data.isRanged;
        if (currentEntity instanceof RangedAttackMob && data.category.equals("Unclassified")) {
            defaultRanged = true;
        }

        int currentX = cardLeft + 10;

        // Exclude Button (Red highlight text)
        Button excludeBtn = Button.builder(Component.literal("§cExclude"), b -> {
            excludeAndNext();
        }).bounds(currentX, bottomRowY, 52, 18).build();
        this.addRenderableWidget(excludeBtn);

        currentX += 56;

        // Checkbox for Ranged
        rangedCheckbox = new Checkbox(currentX, bottomRowY + 1, 62, 18, Component.literal("Ranged"), defaultRanged);
        this.addRenderableWidget(rangedCheckbox);

        currentX += 66;

        String[] categories = new String[]{"Monster", "Elites", "Miniboss", "Boss"};
        int btnWidth = 55;
        int btnHeight = 18;
        int btnSpacing = 4;

        for (int i = 0; i < categories.length; i++) {
            String category = categories[i];
            int btnX = currentX + i * (btnWidth + btnSpacing);

            Button btn = Button.builder(Component.literal(category), b -> {
                classifyAndNext(category);
            }).bounds(btnX, bottomRowY, btnWidth, btnHeight).build();

            this.addRenderableWidget(btn);
        }
    }

    private void excludeAndNext() {
        if (currentIndex >= 0 && currentIndex < mobTypes.size()) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(mobTypes.get(currentIndex));
            if (key != null) {
                boolean isRanged = rangedCheckbox != null && rangedCheckbox.selected();
                ClassificationManager.setClassification(key.toString(), "Excluded", isRanged, true);
            }
        }
        nextMob();
    }

    private void classifyAndNext(String category) {
        if (currentIndex >= 0 && currentIndex < mobTypes.size()) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(mobTypes.get(currentIndex));
            if (key != null) {
                boolean isRanged = rangedCheckbox != null && rangedCheckbox.selected();
                ClassificationManager.setClassification(key.toString(), category, isRanged, false);
            }
        }
        nextMob();
    }

    private void nextMob() {
        if (mobTypes.isEmpty()) return;
        currentIndex = (currentIndex + 1) % mobTypes.size();
        updateCurrentEntity();
        buildClassificationButtons();
    }

    private void previousMob() {
        if (mobTypes.isEmpty()) return;
        currentIndex = (currentIndex - 1 + mobTypes.size()) % mobTypes.size();
        updateCurrentEntity();
        buildClassificationButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int cardLeft = (this.width - CARD_WIDTH) / 2;
        int cardTop = (this.height - CARD_HEIGHT) / 2;

        graphics.fill(cardLeft, cardTop, cardLeft + CARD_WIDTH, cardTop + CARD_HEIGHT, 0xF0181818);
        graphics.renderOutline(cardLeft, cardTop, CARD_WIDTH, CARD_HEIGHT, 0xFF444444);
        graphics.renderOutline(cardLeft + 1, cardTop + 1, CARD_WIDTH - 2, CARD_HEIGHT - 2, 0xFF222222);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (mobTypes.isEmpty() || currentEntity == null) {
            graphics.drawCenteredString(this.font, "No hostile mobs found", this.width / 2, this.height / 2, 0xFFFFFF);
            return;
        }

        EntityType<?> type = mobTypes.get(currentIndex);
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        String mobIdStr = key != null ? key.toString() : "Unknown";

        int centerX = this.width / 2;

        // Header Title in Box Header
        int headerY = cardTop + 14;
        String progressTitle = "Hostile Classifier (" + (currentIndex + 1) + "/" + mobTypes.size() + ")";
        graphics.drawCenteredString(this.font, progressTitle, centerX, headerY, 0xFFDD00);

        // 1. Render Mob in middle of card
        int renderY = cardTop + 85;
        int scale = 32;

        float entityHeight = currentEntity.getBbHeight();
        if (entityHeight > 0) {
            scale = (int) Math.max(10, Math.min(42, 42 / Math.max(1.0f, entityHeight)));
        }

        float mouseAngleX = (float) (centerX - mouseX);
        float mouseAngleY = (float) (renderY - 25 - mouseY);

        renderLivingEntity(graphics, centerX, renderY, scale, mouseAngleX, mouseAngleY, currentEntity);

        // 2. Mob Name directly BELOW entity render
        int textY = renderY + 8;
        Component displayName = currentEntity.getDisplayName();
        graphics.drawCenteredString(this.font, displayName.getString(), centerX, textY, 0xFFFFFF);

        // Subtitle: Current classification state & Registry ID
        textY += 10;
        ClassificationManager.MobData data = ClassificationManager.getMobData(mobIdStr);
        String catDisplay = data.excluded ? "§cExcluded§r" : data.category;
        String subTitle = "Current: " + catDisplay + " | Ranged: " + (data.isRanged ? "Yes" : "No") + " [" + mobIdStr + "]";
        graphics.drawCenteredString(this.font, subTitle, centerX, textY, 0x888888);

        // 3. Compact Stats line directly below name
        textY += 11;
        double hp = currentEntity.getAttributeValue(Attributes.MAX_HEALTH);
        double damage = currentEntity.getAttribute(Attributes.ATTACK_DAMAGE) != null ?
                currentEntity.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0.0;
        double armor = currentEntity.getAttribute(Attributes.ARMOR) != null ?
                currentEntity.getAttributeValue(Attributes.ARMOR) : 0.0;
        boolean isBossRegistered = BossManager.isBoss(currentEntity);

        String statsLine = String.format("HP: %.0f  •  Dmg: %.0f  •  Armor: %.0f  •  BossBar: %s", hp, damage, armor, isBossRegistered ? "Yes" : "No");
        graphics.drawCenteredString(this.font, statsLine, centerX, textY, 0xFF5555);
    }

    private static void renderLivingEntity(GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY, LivingEntity entity) {
        float xAngle = (float) Math.atan((x - mouseX) / 40.0F);
        float yAngle = (float) Math.atan((y - mouseY) / 40.0F);
        InventoryScreen.renderEntityInInventoryFollowsAngle(
                graphics, x, y, scale, xAngle, yAngle, entity
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
