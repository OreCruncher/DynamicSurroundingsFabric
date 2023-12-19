package org.orecruncher.dsurround.gui.debug;

import com.google.common.base.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.runtime.diagnostics.Diagnostics;

/***
 * Our debug and diagnostics hud.  Derived from DebugHud.
 */
@Environment(EnvType.CLIENT)
public class DiagnosticsHud {

    private static final int backgroundColor = -1873784752;
    private static final int foregroundColor = 14737632;

    private final MinecraftClient client;
    private final TextRenderer textRenderer;

    private final Diagnostics diagnostics;

    public DiagnosticsHud(MinecraftClient client) {
        this.client = client;
        this.textRenderer = client.textRenderer;
        this.diagnostics = ContainerManager.resolve(Diagnostics.class);
    }

    public void render(DrawContext context) {
        // Only want to rendered if configured to do so and when the regular
        // diagnostic menu is not showing
        if (this.diagnostics.isCollecting() && !this.isDebugHudEnabled()) {
            this.renderLeftText(context);
            this.renderRightText(context);
        }
    }

    protected void renderLeftText(DrawContext context) {
        ObjectArray<String> list = this.diagnostics.getLeft();
        this.drawText(context, list, true);
    }

    protected void renderRightText(DrawContext context) {
        ObjectArray<String> list = this.diagnostics.getRight();
        this.drawText(context, list, false);
    }

    private boolean isDebugHudEnabled()
    {
        return this.client.getDebugHud().shouldShowDebugHud();
    }

    private void drawText(DrawContext context, ObjectArray<String> text, boolean left) {
        int m;
        int l;
        int k;
        String string;
        int j;
        int i = this.textRenderer.fontHeight;
        for (j = 0; j < text.size(); ++j) {
            string = text.get(j);
            if (Strings.isNullOrEmpty(string)) continue;
            k = this.textRenderer.getWidth(string);
            l = left ? 2 : context.getScaledWindowWidth() - 2 - k;
            m = 2 + i * j;
            context.fill(l - 1, m - 1, l + k + 1, m + i - 1, backgroundColor);
        }
        for (j = 0; j < text.size(); ++j) {
            string = text.get(j);
            if (Strings.isNullOrEmpty(string)) continue;
            k = this.textRenderer.getWidth(string);
            l = left ? 2 : context.getScaledWindowWidth() - 2 - k;
            m = 2 + i * j;
            context.drawText(this.textRenderer, string, l, m, foregroundColor, false);
        }
    }
}
