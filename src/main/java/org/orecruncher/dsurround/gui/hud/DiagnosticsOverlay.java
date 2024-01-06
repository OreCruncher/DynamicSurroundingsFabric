package org.orecruncher.dsurround.gui.hud;

import com.google.common.base.Strings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.gui.hud.plugins.*;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.lib.platform.ModInformation;
import org.orecruncher.dsurround.lib.math.LoggingTimerEMA;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

/***
 * Our debug and diagnostics overlay.  Derived from DebugHud.
 */
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
        var platformName = ContainerManager.resolve(IPlatform.class).getPlatformName();
        this.branding = "%s (%s)".formatted(modInformation.getBranding(), platformName);
        this.showHud = false;

        this.plugins.add(new ClientProfilerPlugin());
        this.plugins.add(new BlockViewerPlugin(ContainerManager.resolve(IBlockLibrary.class), ContainerManager.resolve(ITagLibrary.class)));
        this.plugins.add(new RuntimeDiagnosticsPlugin(ContainerManager.resolve(IConditionEvaluator.class)));
        this.plugins.add(new SoundEngineDiagnosticsPlugin());
    }

    public void toggleCollection() {
        this.enableCollection = !this.enableCollection;
    }

    @Override
    public void tick(Minecraft client) {
        // Only want to render if configured to do so and when the regular
        // diagnostic menu is not showing
        this.showHud = this.enableCollection && !this.isDebugHudEnabled();

        if (this.showHud) {

            // Perform tick on the plugins
            this.plugins.forEach(p -> p.tick(client));

            this.diagnostics.begin();

            var event = new ClientEventHooks.CollectDiagnosticsEvent();

            event.left.add(this.branding);

            ClientEventHooks.COLLECT_DIAGNOSTICS.raise(event);

            event.timers.add(diagnostics);

            this.left = event.left;
            this.right = new ObjectArray<>(event.right.size() + event.timers.size() + 1);

            for (var timer : event.timers)
                this.right.add(ChatFormatting.LIGHT_PURPLE + timer.toString());

            this.right.add(joptsimple.internal.Strings.EMPTY);
            this.right.addAll(event.right);

            this.diagnostics.end();
        }
    }

    @Override
    public void render(GuiGraphics context) {
        if (this.showHud) {
            this.drawText(context, this.left, true);
            this.drawText(context, this.right, false);
        }
    }

    private boolean isDebugHudEnabled() {
        return GameUtils.isInGame() && GameUtils.getMC().getDebugOverlay().showDebugScreen();
    }

    private void drawText(GuiGraphics context, ObjectArray<String> text, boolean left) {
        var textRenderer = GameUtils.getTextRenderer();
        int m;
        int l;
        int k;
        String string;
        int j;
        int i = textRenderer.lineHeight;
        for (j = 0; j < text.size(); ++j) {
            string = text.get(j);
            if (Strings.isNullOrEmpty(string)) continue;
            k = textRenderer.width(string);
            l = left ? 2 : context.guiWidth() - 2 - k;
            m = 2 + i * j;
            context.fill(l - 1, m - 1, l + k + 1, m + i - 1, backgroundColor);
        }
        for (j = 0; j < text.size(); ++j) {
            string = text.get(j);
            if (Strings.isNullOrEmpty(string)) continue;
            k = textRenderer.width(string);
            l = left ? 2 : context.guiWidth() - 2 - k;
            m = 2 + i * j;
            context.drawString(textRenderer, string, l, m, foregroundColor, false);
        }
    }
}
