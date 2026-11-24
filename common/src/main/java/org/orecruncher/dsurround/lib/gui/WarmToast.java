// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.List;

/**
 * A toast with a title and a message, each wrapped to as many lines as they need, laid out as the game's
 * {@code SystemToast} is. The background is one {@code blitSprite} of a nine-sliced sprite, the game's
 * {@code toast/now_playing} (its own music toast's: the plain dark box 1.21.1 drew from {@code toast/advancement} in
 * pieces), which keeps its edges at any size; {@code toast/advancement} has no nine-slice scaling and would stretch.
 */
public final class WarmToast implements Toast {
    public static final Identifier NOW_PLAYING_SPRITE = Identifier.withDefaultNamespace("toast/now_playing");
    private static final Profile DEFAULT_PROFILE = new Profile(NOW_PLAYING_SPRITE, 5000, ColorPalette.GOLD, ColorPalette.WHITE);

    private static final int MAX_LINE_SIZE = 200;
    private static final int MIN_LINE_SIZE = 100;
    private static final int LINE_SPACING = 12;
    private static final int TEXT_X = 18;
    private static final int RIGHT_MARGIN = 12;

    private final Profile profile;

    private List<FormattedCharSequence> title;
    private List<FormattedCharSequence> messageLines;
    private long lastChanged;
    private boolean changed;
    private int width;
    private Toast.Visibility wantedVisibility;

    public static WarmToast from(Component title, Component body) {
        return from(DEFAULT_PROFILE, title, body);
    }

    public static WarmToast from(Profile profile, Component title, Component body) {
        return new WarmToast(profile, title, body);
    }

    private WarmToast(Profile profile, Component title, Component body) {
        this.wantedVisibility = Visibility.HIDE;
        this.profile = profile;
        this.update(title, body);
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        // As SystemToast's: a margin of 10 above and below, every line of the title and the message
        return 20 + (this.title.size() - 1) * LINE_SPACING + Math.max(this.messageLines.size(), 1) * LINE_SPACING;
    }

    public void reset(Component title, Component message) {
        this.update(title, message);
        this.changed = true;
    }

    @Override
    public @NonNull Visibility getWantedVisibility() {
        return this.wantedVisibility;
    }

    @Override
    public void update(final @NonNull ToastManager manager, final long fullyVisibleForMs) {
        if (this.changed) {
            this.lastChanged = fullyVisibleForMs;
            this.changed = false;
        }

        // The profile's time, scaled by the game's Notification Time option
        double timeToDisplay = this.profile.displayTime() * manager.getNotificationDisplayTimeMultiplier();
        long timeSinceUpdate = fullyVisibleForMs - this.lastChanged;
        this.wantedVisibility = timeSinceUpdate < timeToDisplay ? Visibility.SHOW : Visibility.HIDE;
    }

    private void update(final Component title, final Component message) {
        var font = GameUtils.getMC().font;
        this.title = font.split(title, MAX_LINE_SIZE);
        this.messageLines = font.split(message, MAX_LINE_SIZE);

        var widest = MIN_LINE_SIZE;
        for (var line : this.title)
            widest = Math.max(widest, font.width(line));
        for (var line : this.messageLines)
            widest = Math.max(widest, font.width(line));
        this.width = TEXT_X + widest + RIGHT_MARGIN;
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor guiGraphics, final @NonNull Font font, final long fullyVisibleForMs) {
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.profile.sprite(), 0, 0, this.width(), this.height());

        int titleColor = opaque(this.profile.titleColor());
        if (this.messageLines.isEmpty()) {
            this.extractTextLines(guiGraphics, font, this.title, 12, titleColor);
        } else {
            this.extractTextLines(guiGraphics, font, this.title, 7, titleColor);
            this.extractTextLines(guiGraphics, font, this.messageLines, 7 + this.title.size() * LINE_SPACING, opaque(this.profile.bodyColor()));
        }
    }

    private void extractTextLines(final GuiGraphicsExtractor graphics, final Font font, final List<FormattedCharSequence> textLines, final int yStart, final int textColor) {
        for (int i = 0; i < textLines.size(); ++i) {
            graphics.text(font, textLines.get(i), TEXT_X, yStart + i * LINE_SPACING, textColor, false);
        }
    }

    // Text colors are ARGB: a TextColor has no alpha, and text with none is invisible
    private static int opaque(TextColor color) {
        return 0xFF000000 | color.getValue();
    }

    /**
     * How a toast looks and how long it stays. The sprite should have nine-slice scaling (as the game's
     * {@code toast/now_playing} has), since the toast's size follows its text.
     */
    public record Profile(Identifier sprite, int displayTime, TextColor titleColor, TextColor bodyColor) {

        public static Profile of(Identifier sprite, int displayTime, TextColor titleColor, TextColor bodyColor) {
            return new Profile(sprite, displayTime, titleColor, bodyColor);
        }
    }
}
