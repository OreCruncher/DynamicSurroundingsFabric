package org.orecruncher.dsurround.gui.debug;

import com.google.common.base.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import org.orecruncher.dsurround.eventing.handlers.DiagnosticHandler;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

@Environment(EnvType.CLIENT)
public class DiagnosticsHud extends DrawableHelper {

    private static final int backgroundColor = -1873784752;
    private static final int foregroundColor = 14737632;

    private final MinecraftClient client;
    private final TextRenderer textRenderer;

    public DiagnosticsHud(MinecraftClient client) {
        this.client = client;
        this.textRenderer = client.textRenderer;
    }

    public void render(MatrixStack matrices) {
        // Only want to rendered if configured to do so and when the regular
        // diagnostic menu is not showing
        if (DiagnosticHandler.isCollecting() && !client.options.debugEnabled) {
            this.renderLeftText(matrices);
            this.renderRightText(matrices);
        }
    }

    protected void renderLeftText(MatrixStack matrices) {
        ObjectArray<String> list = DiagnosticHandler.getLeft();

        for (int i = 0; i < list.size(); ++i) {
            String string = list.get(i);
            if (!Strings.isNullOrEmpty(string)) {
                int j = 9;
                int k = this.textRenderer.getWidth(string);
                int m = 2 + j * i;
                fill(matrices, 1, m - 1, 2 + k + 1, m + j - 1, backgroundColor);
                this.textRenderer.draw(matrices, string, 2.0F, (float) m, foregroundColor);
            }
        }
    }

    protected void renderRightText(MatrixStack matrices) {
        ObjectArray<String> list = DiagnosticHandler.getRight();

        int scaledWidth = this.client.getWindow().getScaledWidth() - 2;
        for (int i = 0; i < list.size(); ++i) {
            String string = list.get(i);
            if (!Strings.isNullOrEmpty(string)) {
                int j = 9;
                int k = this.textRenderer.getWidth(string);
                int l = scaledWidth - k;
                int m = 2 + j * i;
                fill(matrices, l - 1, m - 1, l + k + 1, m + j - 1, backgroundColor);
                this.textRenderer.draw(matrices, string, (float) l, (float) m, foregroundColor);
            }
        }
    }
}
