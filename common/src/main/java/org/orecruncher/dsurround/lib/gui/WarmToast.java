package org.orecruncher.dsurround.lib.gui;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.List;

public final class WarmToast implements Toast {
    private static final Profile DEFAULT_PROFILE = new Profile(Identifier.withDefaultNamespace("toast/advancement"), 5000, ColorPalette.GOLD, ColorPalette.WHITE);

    private static final int MAX_LINE_SIZE = 200;
    private static final int MIN_LINE_SIZE = 100;
    private static final int LINE_SPACING = 12;
    private static final int MARGIN = 10;

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

    public int width() {
        return this.width;
    }

    public int height() {
        return MARGIN * 2 + Math.max(this.messageLines.size(), 1) * LINE_SPACING;
    }

    public void reset(Component title, @Nullable Component message) {
        this.update(title, message);
        this.changed = true;
    }

    @Override
    public @NonNull Visibility getWantedVisibility() {
        return this.wantedVisibility;
    }

    public void update(final @NonNull ToastManager manager, final long fullyVisibleForMs) {
        if (this.changed) {
            this.lastChanged = fullyVisibleForMs;
            this.changed = false;
        }

        // TODO: What to use for 5
        double timeToDisplayUpdate = 5 * manager.getNotificationDisplayTimeMultiplier();
        long timeSinceUpdate = fullyVisibleForMs - this.lastChanged;
        this.wantedVisibility = (double)timeSinceUpdate < timeToDisplayUpdate ? Visibility.SHOW : Visibility.HIDE;
    }

    private void update(final Component title, final Component message) {
        var font = GameUtils.getMC().font;
        this.messageLines = font.split(message, MAX_LINE_SIZE);
        this.title = font.split(title, MAX_LINE_SIZE);

        var titleSize = this.title.stream().mapToInt(font::width).max().orElse(MIN_LINE_SIZE);

        if (titleSize < MIN_LINE_SIZE) {
            titleSize = MIN_LINE_SIZE;
        }

        var lineSize = this.messageLines.stream().mapToInt(font::width).max().orElse(MIN_LINE_SIZE);
        this.width = Math.max(titleSize, lineSize) + MARGIN * 3;
    }

    public void extractRenderState(final @NonNull GuiGraphicsExtractor guiGraphics, final @NonNull Font font, final long fullyVisibleForMs) {
        int i = this.width();
        if (i == 160 && this.messageLines.size() <= 1) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.profile.sprite, 0, 0, i, this.height());
        } else {
            int renderHeight = this.height();
            int lineRenderCount = Math.min(4, renderHeight - 28);
            this.renderBackgroundRow(guiGraphics, i, 0, 0, 28);

            for(int n = 28; n < renderHeight - lineRenderCount; n += 10) {
                this.renderBackgroundRow(guiGraphics, i, 16, n, Math.min(16, renderHeight - n - lineRenderCount));
            }

            this.renderBackgroundRow(guiGraphics, i, 32 - lineRenderCount, renderHeight - lineRenderCount, lineRenderCount);
        }

        if (this.messageLines.isEmpty()) {
            this.extractTextLines(guiGraphics, font, this.title, 12, -256);
        } else {
            this.extractTextLines(guiGraphics, font, this.title, 7, -256);
            this.extractTextLines(guiGraphics, font, this.messageLines, 7 + this.title.size() * 12, -1);
        }
    }

    private void renderBackgroundRow(GuiGraphicsExtractor guiGraphics, int i, int j, int k, int l) {
        int m = j == 0 ? 20 : 5;
        int n = Math.min(60, i - m);
        guiGraphics.blit(this.profile.sprite, 160, 32, 0, j, 0, k, m, l);

        for(int o = m; o < i - n; o += 64) {
            guiGraphics.blit(this.profile.sprite, 160, 32, 32, j, o, k, Math.min(64, i - o - n), l);
        }

        guiGraphics.blit(this.profile.sprite, 160, 32, 160 - n, j, i - n, k, n, l);
    }

    private void extractTextLines(final GuiGraphicsExtractor graphics, final Font font, final List<FormattedCharSequence> textLines, final int yStart, final int textColor) {
        for(int i = 0; i < textLines.size(); ++i) {
            graphics.text(font, (FormattedCharSequence)textLines.get(i), 18, yStart + i * 12, textColor, false);
        }
    }

    private static ImmutableList<FormattedCharSequence> nullToEmpty(@Nullable Component component) {
        return component == null ? ImmutableList.of() : ImmutableList.of(component.getVisualOrderText());
    }

    public record Profile(Identifier sprite, int displayTime, TextColor titleColor, TextColor bodyColor) {

        public static Profile of(Identifier sprite, int displayTime, TextColor titleColor, TextColor bodyColor) {
            return new Profile(sprite, displayTime, titleColor, bodyColor);
        }
    }
}
