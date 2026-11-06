// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.MarginPosition;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.List;

/**
 * A portrait of the mob the player is looking at, in the top left of the screen: its head, name and health, with a bar
 * colored for how it stands toward the player, and a row of badges under the portrait for its effects (see
 * {@link TargetEffects}), only when it has some. Looks for a target once a tick, stopped by blocks, and stays a few
 * seconds after the player looks away. Mobs only: players would be an advantage in a fight.
 * <p>
 * Drawn with the game's own sprites, a dark toast for the panels and a boss bar stretched to fit for the health, so a
 * resource pack that restyles those restyles this.
 */
public final class TargetPortraitOverlay extends AbstractOverlay {

    private static final Identifier PANEL = Identifier.withDefaultNamespace("toast/now_playing");
    // Vanilla's frame for an effect on the HUD
    private static final Identifier BADGE_BACKGROUND = Identifier.withDefaultNamespace("hud/effect_background");
    private static final int BACKDROP_TOP = 0xFF8C9AAE;
    private static final int BACKDROP_BOTTOM = 0xFF5C6778;
    private static final Bar HOSTILE_BAR = new Bar("red");
    private static final Bar NEUTRAL_BAR = new Bar("yellow");
    private static final Bar PASSIVE_BAR = new Bar("green");

    // In GUI pixels, at a scale of 1
    private static final int PORTRAIT_SIZE = 36;
    private static final int PORTRAIT_INSET = 4;
    private static final int INFO_LEFT = PORTRAIT_SIZE + 2;
    private static final int INFO_HEIGHT = PORTRAIT_SIZE;
    private static final int INFO_PADDING = 5;
    private static final int NAME_TOP = 7;
    private static final int BAR_TOP = 18;
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 10;
    private static final int BADGE_SIZE = 16;
    private static final int BADGE_ICON_INSET = 2;
    private static final int BADGE_GAP = 1;
    private static final int BADGES_TOP = PORTRAIT_SIZE + 2;
    private static final int MAX_BADGES = 8;

    // How far it looks, and how many ticks a target stays once the player looks away, and farther than that it goes
    static final double RANGE = 32D;
    static final int LINGER_TICKS = 60;
    private static final double LOST_RANGE_SQ = 48D * 48D;

    private static final float HORSE_BLOCKS = 0.95F;
    private static final float HORSE_LIFT = 0.2F;
    // Tuned by eye: a plain horse's head reads larger than the undead ones' and a donkey's, the same size
    private static final float PLAIN_HORSE_BLOCKS = 1.15F;
    private static final float PLAIN_HORSE_LIFT = 0.28F;

    private final Configuration.TargetPortraitOptions config;
    private final Configuration.CompassAndClockOptions margin;
    private final MarginLayout marginLayout;

    private int targetId = -1;
    private int lingering;
    private List<Holder<MobEffect>> badges = List.of();

    public TargetPortraitOverlay(Configuration config, MarginLayout marginLayout) {
        this.config = config.targetPortraitOptions;
        this.margin = config.compassAndClockOptions;
        this.marginLayout = marginLayout;
    }

    @Override
    public void tick(Minecraft client) {
        if (!this.config.enableTargetPortrait || !GameUtils.isInGame()) {
            this.targetId = -1;
            return;
        }

        var player = GameUtils.getPlayer().orElseThrow();
        var seen = lookedAt(player);
        if (seen != null) {
            this.targetId = seen.getId();
            this.lingering = LINGER_TICKS;
        } else if (this.lingering > 0) {
            this.lingering--;
        }

        var target = this.target(client);
        if (target == null || this.lingering <= 0 || !target.isAlive() || target.distanceToSqr(player) > LOST_RANGE_SQ) {
            this.targetId = -1;
            this.badges = List.of();
        } else {
            this.badges = this.config.showEffects ? TargetEffects.of(target) : List.of();
        }
    }

    private LivingEntity target(Minecraft client) {
        if (this.targetId < 0 || client.level == null)
            return null;
        return client.level.getEntity(this.targetId) instanceof LivingEntity living ? living : null;
    }

    /**
     * The mob the player is looking at, if nothing solid is in the way
     */
    private static LivingEntity lookedAt(Player player) {
        var eye = player.getEyePosition();
        var view = player.getViewVector(1F);
        var end = eye.add(view.scale(RANGE));
        var block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS)
            end = block.getLocation();
        var area = player.getBoundingBox().expandTowards(view.scale(RANGE)).inflate(1D);
        var hit = ProjectileUtil.getEntityHitResult(player, eye, end, area, e -> isTarget(e, player), eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static boolean isTarget(Entity entity, Player player) {
        return entity instanceof LivingEntity && !(entity instanceof Player) && !(entity instanceof ArmorStand)
                && entity.isPickable() && !entity.isInvisibleTo(player);
    }

    @Override
    public void render(GuiGraphicsExtractor context, float partialTick) {
        if (!this.config.enableTargetPortrait)
            return;
        var target = this.target(Minecraft.getInstance());
        if (target == null)
            return;

        float scale = (float) this.config.scale;
        int left = Math.round(MarginLayout.EDGE_GAP);
        int top = Math.round(Math.max(MarginLayout.EDGE_GAP, this.marginTop()));
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(left, top);
        pose.scale(scale, scale);

        context.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, 0, 0, PORTRAIT_SIZE, PORTRAIT_SIZE);
        // Lighter than the panel, or a dark mob is lost on it
        context.fillGradient(PORTRAIT_INSET, PORTRAIT_INSET, PORTRAIT_SIZE - PORTRAIT_INSET, PORTRAIT_SIZE - PORTRAIT_INSET, BACKDROP_TOP, BACKDROP_BOTTOM);
        pose.popMatrix();
        drawHead(context, target, left, top, scale, partialTick);
        pose.pushMatrix();
        pose.translate(left, top);
        pose.scale(scale, scale);

        var font = GameUtils.getTextRenderer();
        var name = target.getDisplayName();
        int textLeft = INFO_LEFT + INFO_PADDING;
        int infoWidth = Math.max(BAR_WIDTH, font.width(name)) + INFO_PADDING * 2;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, INFO_LEFT, 0, infoWidth, INFO_HEIGHT);
        context.text(font, name, textLeft, NAME_TOP, -1, true);

        float health = Math.max(0F, target.getHealth());
        float maxHealth = Math.max(1F, target.getMaxHealth());
        var bar = barFor(target);
        context.blitSprite(RenderPipelines.GUI_TEXTURED, bar.background(), textLeft, BAR_TOP, BAR_WIDTH, BAR_HEIGHT);
        int filled = barFill(health, maxHealth, BAR_WIDTH);
        if (filled > 0) {
            context.enableScissor(textLeft, BAR_TOP, textLeft + filled, BAR_TOP + BAR_HEIGHT);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, bar.progress(), textLeft, BAR_TOP, BAR_WIDTH, BAR_HEIGHT);
            context.disableScissor();
        }

        var numbers = "%d / %d".formatted(Mth.ceil(health), Mth.ceil(maxHealth));
        context.text(font, numbers, textLeft + (BAR_WIDTH - font.width(numbers)) / 2, BAR_TOP + 1, -1, true);

        int count = Math.min(this.badges.size(), MAX_BADGES);
        for (int i = 0; i < count; i++) {
            int x = i * (BADGE_SIZE + BADGE_GAP);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, BADGE_BACKGROUND, x, BADGES_TOP, BADGE_SIZE, BADGE_SIZE);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(this.badges.get(i)), x + BADGE_ICON_INSET,
                    BADGES_TOP + BADGE_ICON_INSET, BADGE_SIZE - BADGE_ICON_INSET * 2, BADGE_SIZE - BADGE_ICON_INSET * 2);
        }
        pose.popMatrix();
    }

    /**
     * How far down the compass and watch reach in the top left corner, if that's where they are
     */
    private float marginTop() {
        if (!this.margin.showInMargin || this.margin.marginPosition != MarginPosition.TOP_LEFT)
            return 0F;
        return this.marginLayout.topLeftReach(MarginPosition.TOP_LEFT) + MarginLayout.ITEM_GAP;
    }

    /**
     * Draws the mob's head in the portrait, as vanilla's inventory does a whole body, close up and turned a little. Not
     * under the pose: it is drawn to a target of its own, placed by the screen rectangle given here.
     */
    private static void drawHead(GuiGraphicsExtractor context, LivingEntity entity, int left, int top, float scale, float partialTick) {
        int inset = Math.round(PORTRAIT_INSET * scale);
        int size = Math.round(PORTRAIT_SIZE * scale);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var renderState = dispatcher.getRenderer(entity).createRenderState(entity, partialTick);
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;
        if (renderState instanceof LivingEntityRenderState living) {
            // Facing straight out of the portrait: turned, a head out in front of a body or a long muzzle is off to a side
            living.bodyRot = 180F;
            living.yRot = 0F;
            if (living.pose != Pose.FALL_FLYING)
                living.xRot = 0F;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1F;
        }
        // A horse's head is on a long neck, above its eyes' height for the model, and small next to its body
        boolean horse = entity instanceof AbstractHorse;
        float blocks = entity instanceof Horse ? PLAIN_HORSE_BLOCKS : horse ? HORSE_BLOCKS : headBlocks(entity.getBbHeight(), entity.getBbWidth());
        var rotation = new Quaternionf().rotateZ((float) Math.PI);
        float middle = entity.getEyeHeight() / entity.getScale() + (entity instanceof Horse ? PLAIN_HORSE_LIFT : horse ? HORSE_LIFT : 0F);
        var translation = new Vector3f(0F, middle, 0F);
        context.entity(renderState, (size - inset * 2) / blocks, translation, rotation, null,
                left + inset, top + inset, left + size - inset, top + size - inset);
    }

    /**
     * How many blocks of a mob of this size fit across the portrait, to crop it to its head: a wide mob has a long head
     * for its height, as a horse does
     */
    static float headBlocks(float height, float width) {
        return Mth.clamp(Math.max(height * 0.45F, width * 0.7F), 0.45F, 1.6F);
    }

    /**
     * How many of the bar's pixels are filled: at least one while it has any health
     */
    static int barFill(float health, float maxHealth, int width) {
        if (health <= 0F)
            return 0;
        return Mth.clamp(Math.round(width * health / maxHealth), 1, width);
    }

    private static Bar barFor(LivingEntity entity) {
        if (entity instanceof Enemy)
            return HOSTILE_BAR;
        return entity instanceof NeutralMob ? NEUTRAL_BAR : PASSIVE_BAR;
    }

    /**
     * A color of boss bar's sprites
     */
    private record Bar(Identifier background, Identifier progress) {
        Bar(String color) {
            this(Identifier.withDefaultNamespace("boss_bar/" + color + "_background"), Identifier.withDefaultNamespace("boss_bar/" + color + "_progress"));
        }
    }
}
