package org.orecruncher.dsurround.gui.hud;

import com.google.common.base.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Formatting;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.gui.hud.plugins.*;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.infra.ModInformation;
import org.orecruncher.dsurround.lib.math.LoggingTimerEMA;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

/***
 * Our debug and diagnostics overlay.  Derived from DebugHud.
 */
@Environment(EnvType.CLIENT)
public class DiagnosticsOverlay extends AbstractOverlay {

    private static final int backgroundColor = -1873784752;
    private static final int foregroundColor = 14737632;

    private final LoggingTimerEMA diagnostics = new LoggingTimerEMA("Diagnostics");
    private final String branding;
    private final ObjectArray<IDiagnosticPlugin> plugins = new ObjectArray<>();

    private boolean showHud;
    private boolean enableCollection = false;
    private ObjectArray<String> left = new ObjectArray<>();
    private ObjectArray<String> right = new ObjectArray<>();

    public DiagnosticsOverlay(ModInformation modInformation) {
        this.branding = modInformation.get_branding();
        this.showHud = false;

        this.plugins.add(new ClientProfilerPlugin());
        this.plugins.add(new BlockViewerPlugin(ContainerManager.resolve(IBlockLibrary.class)));
        this.plugins.add(new RuntimeDiagnosticsPlugin(ContainerManager.resolve(IConditionEvaluator.class)));
        this.plugins.add(new SoundEngineDiagnosticsPlugin());
    }

    public void toggleCollection() {
        this.enableCollection = !this.enableCollection;
    }

    @Override
    public void tick(MinecraftClient client) {
        // Only want to rendered if configured to do so and when the regular
        // diagnostic menu is not showing
        this.showHud = this.enableCollection && !this.isDebugHudEnabled();

        if (this.showHud) {
            this.diagnostics.begin();

            var event = new ClientEventHooks.CollectDiagnosticsEvent();

            event.left.add(this.branding);

            ClientEventHooks.COLLECT_DIAGNOSTICS.raise(event);

            event.timers.add(diagnostics);

            this.left = event.left;
            this.right = new ObjectArray<>(event.right.size() + event.timers.size() + 1);

            for (var timer : event.timers)
                this.right.add(Formatting.LIGHT_PURPLE + timer.toString());

            this.right.add(joptsimple.internal.Strings.EMPTY);
            this.right.addAll(event.right);

            this.diagnostics.end();
        }
    }

    @Override
    public void render(DrawContext context) {
        if (this.showHud) {
            this.drawText(context, this.left, true);
            this.drawText(context, this.right, false);
        }
    }

    private boolean isDebugHudEnabled() {
        return GameUtils.isInGame() && GameUtils.getMC().getDebugHud().shouldShowDebugHud();
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
