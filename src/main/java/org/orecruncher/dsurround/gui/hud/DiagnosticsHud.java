package org.orecruncher.dsurround.gui.hud;

import com.google.common.base.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.runtime.diagnostics.Diagnostics;

/***
 * Our debug and diagnostics hud.  Derived from DebugHud.
 */
@Environment(EnvType.CLIENT)
public class DiagnosticsHud extends AbstractHudOverlay {

    private static final int backgroundColor = -1873784752;
    private static final int foregroundColor = 14737632;

    private final Diagnostics diagnostics;
    private boolean showHud;

    public DiagnosticsHud() {
        this.diagnostics = ContainerManager.resolve(Diagnostics.class);
        this.showHud = false;
    }

    @Override
    public void tick(MinecraftClient client) {
        // Only want to rendered if configured to do so and when the regular
        // diagnostic menu is not showing
        this.showHud = this.diagnostics.isCollecting() && !this.isDebugHudEnabled();
    }

    @Override
    public void render(DrawContext context) {
        if (this.showHud) {
            this.drawText(context, this.diagnostics.getLeft(), true);
            this.drawText(context, this.diagnostics.getRight(), false);
        }
    }

    private boolean isDebugHudEnabled() {
        return GameUtils.getMC().getDebugHud().shouldShowDebugHud();
    }

    private void drawText(DrawContext context, ObjectArray<String> text, boolean left) {
        var textRenderer = GameUtils.getTextRenderer();
        int m;
        int l;
        int k;
        String string;
        int j;
        int i = textRenderer.fontHeight;
        for (j = 0; j < text.size(); ++j) {
            string = text.get(j);
            if (Strings.isNullOrEmpty(string)) continue;
            k = textRenderer.getWidth(string);
            l = left ? 2 : context.getScaledWindowWidth() - 2 - k;
            m = 2 + i * j;
            context.fill(l - 1, m - 1, l + k + 1, m + i - 1, backgroundColor);
        }
        for (j = 0; j < text.size(); ++j) {
            string = text.get(j);
            if (Strings.isNullOrEmpty(string)) continue;
            k = textRenderer.getWidth(string);
            l = left ? 2 : context.getScaledWindowWidth() - 2 - k;
            m = 2 + i * j;
            context.drawText(textRenderer, string, l, m, foregroundColor, false);
        }
    }
}
