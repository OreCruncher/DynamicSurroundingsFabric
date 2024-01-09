package org.orecruncher.dsurround.gui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.config.libraries.IEntityEffectLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.gui.hud.plugins.*;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.lib.platform.ModInformation;
import org.orecruncher.dsurround.lib.math.LoggingTimerEMA;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/***
 * Our debug and diagnostics overlay.  Derived from DebugHud.
 */
public class DiagnosticsOverlay extends AbstractOverlay {

    private static final int BACKGROUND_COLOR = 0x90505050;     // Very dark gray with alpha
    private static final int FOREGROUND_COLOR = 0x00E0E0E0;     // Very light gray

    private static final Map<CollectDiagnosticsEvent.Section, TextColor> COLOR_MAP = new EnumMap<>(CollectDiagnosticsEvent.Section.class);
    private static final ObjectArray<CollectDiagnosticsEvent.Section> RIGHT_SIDE_LAYOUT = new ObjectArray<>();
    private static final ObjectArray<CollectDiagnosticsEvent.Section> LEFT_SIDE_LAYOUT = new ObjectArray<>();

    static {
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.Header, ColorPalette.PUMPKIN_ORANGE);
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.Systems, ColorPalette.GREEN);
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.Timers, ColorPalette.KEY_LIME);
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.Environment, ColorPalette.AQUAMARINE);
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.Emitters, ColorPalette.SEASHELL);
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.Sounds, ColorPalette.APRICOT);
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.BlockView, ColorPalette.BRASS);
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.FluidView, ColorPalette.TURQUOISE);
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.EntityView, ColorPalette.RASPBERRY);
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.Survey, ColorPalette.ORCHID);
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.Misc, ColorPalette.GRAY);

        LEFT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.Header);
        LEFT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.Environment);
        LEFT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.Systems);
        LEFT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.Emitters);
        LEFT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.Sounds);

        RIGHT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.Timers);
        RIGHT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.Survey);
        RIGHT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.Misc);
        RIGHT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.BlockView);
        RIGHT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.FluidView);
        RIGHT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.EntityView);
    }

    private final LoggingTimerEMA diagnostics = new LoggingTimerEMA("Diagnostics");
    private final String branding;
    private final ObjectArray<IDiagnosticPlugin> plugins = new ObjectArray<>();

    private boolean showHud;
    private boolean enableCollection = false;
    private ObjectArray<Component> left = new ObjectArray<>();
    private ObjectArray<Component> right = new ObjectArray<>();

    public DiagnosticsOverlay(ModInformation modInformation) {
        var platformName = ContainerManager.resolve(IPlatform.class).getPlatformName();
        this.branding = "%s (%s)".formatted(modInformation.getBranding(), platformName);
        this.showHud = false;

        this.plugins.add(new ClientProfilerPlugin());
        this.plugins.add(new ViewerPlugin(ContainerManager.resolve(IBlockLibrary.class), ContainerManager.resolve(ITagLibrary.class), ContainerManager.resolve(IEntityEffectLibrary.class)));
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

            var event = new CollectDiagnosticsEvent();
            event.add(CollectDiagnosticsEvent.Section.Header, this.branding);

            ClientEventHooks.COLLECT_DIAGNOSTICS.raise(event);

            event.add(diagnostics);

            this.left = new ObjectArray<>();
            this.right = new ObjectArray<>();

            processOutput(LEFT_SIDE_LAYOUT, event, this.left);
            processOutput(RIGHT_SIDE_LAYOUT, event, this.right);

            this.diagnostics.end();
        }
    }

    private static void processOutput(ObjectArray<CollectDiagnosticsEvent.Section> panels, CollectDiagnosticsEvent event, ObjectArray<Component> result) {
        boolean addBlankLine = false;
        for (var p : panels) {
            var data = event.getSectionText(p);
            if (!data.isEmpty()) {
                if (addBlankLine)
                    result.add(Component.empty());
                else
                    addBlankLine = true;
                var color = COLOR_MAP.get(p);

                if (p.addHeader()) {
                    var style = Style.EMPTY.withColor(color).withUnderlined(true);
                    result.add(Component.literal(p.name()).withStyle(style));
                }

                for (var d : data)
                    result.add(Component.literal(d).withColor(color.getValue()));
            }
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

    private void drawText(GuiGraphics context, ObjectArray<Component> text, boolean left) {
        var textRenderer = GameUtils.getTextRenderer();
        int m;
        int l;
        int k;
        Component string;
        int j;
        int i = textRenderer.lineHeight;
        for (j = 0; j < text.size(); ++j) {
            string = text.get(j);
            if (Objects.equals(string, Component.empty()))
                continue;
            k = textRenderer.width(string);
            l = left ? 2 : context.guiWidth() - 2 - k;
            m = 2 + i * j;
            context.fill(l - 1, m - 1, l + k + 1, m + i - 1, BACKGROUND_COLOR);
        }
        for (j = 0; j < text.size(); ++j) {
            string = text.get(j);
            if (Objects.equals(string, Component.empty()))
                continue;
            k = textRenderer.width(string);
            l = left ? 2 : context.guiWidth() - 2 - k;
            m = 2 + i * j;
            context.drawString(textRenderer, string, l, m, FOREGROUND_COLOR, false);
        }
    }
}
