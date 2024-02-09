package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.gui.overlay.plugins.*;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.lib.platform.ModInformation;
import org.orecruncher.dsurround.lib.math.LoggingTimerEMA;

import java.util.EnumMap;
import java.util.Map;

/***
 * Our debug and diagnostics overlay.  Derived from DebugHud.
 */
public class DiagnosticsOverlay extends AbstractOverlay {

    private static final int BACKGROUND_COLOR = 0x90505050;     // Very dark gray with alpha
    private static final int FOREGROUND_COLOR = 0x00E0E0E0;     // Very light gray

    private static final Style SPECIAL_MOD_STYLE = Style.EMPTY.withColor(ColorPalette.BRASS).withItalic(true);
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
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.HeldItem, ColorPalette.ANTIQUE_WHITE);
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
        RIGHT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.HeldItem);
        RIGHT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.BlockView);
        RIGHT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.FluidView);
        RIGHT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.EntityView);
    }

    private final IPlatform platform;
    private final LoggingTimerEMA diagnostics = new LoggingTimerEMA("Collect Diagnostic");
    private final LoggingTimerEMA rendering = new LoggingTimerEMA("Render Diagnostic");
    private final String branding;
    private final ObjectArray<IDiagnosticPlugin> plugins = new ObjectArray<>();
    private final CollectDiagnosticsEvent reusableEvent = new CollectDiagnosticsEvent();
    private final ObjectArray<FormattedCharSequence> left = new ObjectArray<>(64);
    private final ObjectArray<FormattedCharSequence> right = new ObjectArray<>(64);
    private boolean showHud;
    private boolean enableCollection;
    private String serverBranding;

    public DiagnosticsOverlay(ModInformation modInformation, IPlatform platform) {
        this.platform = platform;
        var platformName = platform.getPlatformName();
        this.branding = "%s (%s)".formatted(modInformation.getBranding(), platformName);
        this.showHud = false;
        this.enableCollection = false;

        // DiagnosticsOverlay is a singleton that makes the following similar to
        // a singleton.
        this.plugins.add(ContainerManager.resolve(ClientProfilerPlugin.class));
        this.plugins.add(ContainerManager.resolve(ViewerPlugin.class));
        this.plugins.add(ContainerManager.resolve(RuntimeDiagnosticsPlugin.class));
        this.plugins.add(ContainerManager.resolve(SoundEngineDiagnosticsPlugin.class));
    }

    public void toggleCollection() {
        this.enableCollection = !this.enableCollection;
    }

    @Override
    public void tick(Minecraft client) {
        // Only want to render if configured to do so and when the regular
        // diagnostic menu is not showing
        this.showHud = this.enableCollection && !this.isDebugHudEnabled();

        // We only want to take the processing hit if the debug overlay is activated
        if (this.showHud) {

            this.diagnostics.begin();

            // Perform tick on the plugins
            this.plugins.forEach(p -> p.tick(client));

            this.reusableEvent.clear();
            this.reusableEvent.add(CollectDiagnosticsEvent.Section.Header, this.branding);

            var serverBrand = GameUtils.getServerBrand();
            serverBrand.ifPresent(brand -> this.reusableEvent.add(CollectDiagnosticsEvent.Section.Header, "Server Brand: %s".formatted(brand)));

            // Check for any special mods and add indicators
            for (var modId : Constants.SPECIAL_MODS)
                if (this.platform.isModLoaded(modId))
                    this.reusableEvent.add(CollectDiagnosticsEvent.Section.Header, Component.literal("MOD: " + modId).withStyle(SPECIAL_MOD_STYLE));

            this.reusableEvent.add(this.diagnostics);
            this.reusableEvent.add(this.rendering);

            ClientEventHooks.COLLECT_DIAGNOSTICS.raise().onCollect(this.reusableEvent);

            this.left.clear();
            this.right.clear();

            processOutput(LEFT_SIDE_LAYOUT, this.reusableEvent, this.left);
            processOutput(RIGHT_SIDE_LAYOUT, this.reusableEvent, this.right);

            this.diagnostics.end();
        }
    }

    private static void processOutput(ObjectArray<CollectDiagnosticsEvent.Section> sections, CollectDiagnosticsEvent event, ObjectArray<FormattedCharSequence> result) {
        boolean addBlankLine = false;
        for (var p : sections) {
            var data = event.getSectionText(p);
            if (!data.isEmpty()) {
                if (addBlankLine)
                    result.add(null);
                else
                    addBlankLine = true;

                var style = Style.EMPTY.withColor(COLOR_MAP.get(p));

                if (p.addHeader()) {
                    var t = Component.literal(p.name()).withStyle(style.withUnderlined(true)).getVisualOrderText();
                    result.add(t);
                }

                for (var d : data) {
                    if (d.getStyle().isEmpty())
                        result.add(d.copy().withStyle(style).getVisualOrderText());
                    else
                        result.add(d.getVisualOrderText());
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics context, float partialTick) {
        if (this.showHud) {
            this.rendering.begin();
            this.drawText(context, this.left, true);
            this.drawText(context, this.right, false);
            this.rendering.end();
        }
    }

    private boolean isDebugHudEnabled() {
        return GameUtils.isInGame() && GameUtils.getMC().options.renderDebug;
    }

    private void drawText(GuiGraphics context, ObjectArray<FormattedCharSequence> text, boolean left) {
        var textRenderer = GameUtils.getTextRenderer();
        int m;
        int l;
        int k;
        FormattedCharSequence component;
        int j;
        int i = textRenderer.lineHeight;
        for (j = 0; j < text.size(); ++j) {
            component = text.get(j);
            if (component == null)
                continue;
            k = textRenderer.width(component);
            l = left ? 2 : context.guiWidth() - 2 - k;
            m = 2 + i * j;
            context.fill(l - 1, m - 1, l + k + 1, m + i - 1, BACKGROUND_COLOR);
            context.drawString(textRenderer, component, l, m, FOREGROUND_COLOR, false);
        }
    }
}
