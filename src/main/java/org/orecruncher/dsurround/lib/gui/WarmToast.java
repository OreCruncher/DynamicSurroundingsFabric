package org.orecruncher.dsurround.lib.gui;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WarmToast  implements Toast {
    private static final ResourceLocation BACKGROUND_SPRITE = new ResourceLocation("toast/advancement");
    private static final int DISPLAY_TIME = 5000;

    private static final int MAX_LINE_SIZE = 200;
    private static final int MIN_LINE_SIZE = 100;
    private static final int LINE_SPACING = 12;
    private static final int MARGIN = 10;

    private final ResourceLocation backgroundSprite;
    private final int displayTime;

    private Component title;
    private List<FormattedCharSequence> messageLines;
    private long lastChanged;
    private boolean changed;
    private final int width;

    public static WarmToast multiline(Minecraft minecraft, Component title, Component body) {
        return multiline(minecraft, BACKGROUND_SPRITE, DISPLAY_TIME, title, body);
    }

    public static WarmToast multiline(Minecraft minecraft, ResourceLocation backgroundSprite, int displayTime, Component title, Component body) {
        var font = minecraft.font;
        var list = font.split(body, MAX_LINE_SIZE);
        var titleSize = Math.min(MAX_LINE_SIZE, Math.max(MIN_LINE_SIZE, font.width(title)));
        var lineSize = list.stream().mapToInt(font::width).max().orElse(MIN_LINE_SIZE);
        int width = Math.max(titleSize, lineSize) + MARGIN * 3;
        return new WarmToast(backgroundSprite, displayTime, title, list, width);
    }

    private WarmToast(ResourceLocation background, int displayTime, Component title, List<FormattedCharSequence> body, int width) {
        this.backgroundSprite = background;
        this.displayTime = displayTime;
        this.title = title;
        this.messageLines = body;
        this.width = width;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return MARGIN * 2 + Math.max(this.messageLines.size(), 1) * LINE_SPACING;
    }

    public void reset(Component component, @Nullable Component component2) {
        this.title = component;
        this.messageLines = nullToEmpty(component2);
        this.changed = true;
    }

    public @NotNull Toast.Visibility render(@NotNull GuiGraphics guiGraphics, @NotNull ToastComponent toastComponent, long lastChanged) {
        if (this.changed) {
            this.lastChanged = lastChanged;
            this.changed = false;
        }

        int i = this.width();
        if (i == 160 && this.messageLines.size() <= 1) {
            guiGraphics.blitSprite(this.backgroundSprite, 0, 0, i, this.height());
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
            guiGraphics.drawString(toastComponent.getMinecraft().font, this.title, 18, LINE_SPACING, -256, false);
        } else {
            guiGraphics.drawString(toastComponent.getMinecraft().font, this.title, 18, 7, -256, false);

            for(int j = 0; j < this.messageLines.size(); ++j) {
                guiGraphics.drawString(toastComponent.getMinecraft().font, this.messageLines.get(j), 18, 18 + j * LINE_SPACING, -1, false);
            }
        }

        double d = (double)this.displayTime * toastComponent.getNotificationDisplayTimeMultiplier();
        long o = lastChanged - this.lastChanged;
        return (double)o < d ? Visibility.SHOW : Visibility.HIDE;
    }

    private void renderBackgroundRow(GuiGraphics guiGraphics, int i, int j, int k, int l) {
        int m = j == 0 ? 20 : 5;
        int n = Math.min(60, i - m);
        guiGraphics.blitSprite(this.backgroundSprite, 160, 32, 0, j, 0, k, m, l);

        for(int o = m; o < i - n; o += 64) {
            guiGraphics.blitSprite(this.backgroundSprite, 160, 32, 32, j, o, k, Math.min(64, i - o - n), l);
        }

        guiGraphics.blitSprite(this.backgroundSprite, 160, 32, 160 - n, j, i - n, k, n, l);
    }

    private static ImmutableList<FormattedCharSequence> nullToEmpty(@Nullable Component component) {
        return component == null ? ImmutableList.of() : ImmutableList.of(component.getVisualOrderText());
    }
}
