package org.orecruncher.dsurround.gui.overlay;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.gui.overlay.plugins.*;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.math.MathStuff;
import org.orecruncher.dsurround.lib.platform.ModInformation;
import org.orecruncher.dsurround.lib.math.LoggingTimerEMA;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/***
 * Our debug and diagnostics overlay.  Derived from DebugHud.
 */
public final class DiagnosticsOverlay extends AbstractOverlay {

    private static final int BACKGROUND_COLOR = 0x90505050;     // Very dark gray with alpha
    private static final int FOREGROUND_COLOR = 0x01E0E0E0;     // Very light gray

    private static final Style BIOME_DIAGNOSTIC_TITLE_COLOR = Style.EMPTY.withColor(ColorPalette.PUMPKIN_ORANGE).withUnderlined(true);
    private static final Style BIOME_DIAGNOSTIC_HEADER_COLOR = Style.EMPTY.withColor(ColorPalette.AQUAMARINE);
    private static final Style BIOME_DIAGNOSTIC_COLOR = Style.EMPTY.withColor(ColorPalette.BRASS);
    private static final Style BIOME_DIAGNOSTIC_MUSIC_ELIGIBLE =  Style.EMPTY.withColor(ColorPalette.ELECTRIC_GREEN);
    private static final Style BIOME_DIAGNOSTIC_MUSIC_NOT_ELIGIBLE =  Style.EMPTY.withColor(ColorPalette.LGRAY);

    private static final Style SPECIAL_MOD_STYLE = Style.EMPTY.withColor(ColorPalette.BRASS).withItalic(true);
    private static final Map<CollectDiagnosticsEvent.Section, TextColor> COLOR_MAP = new EnumMap<>(CollectDiagnosticsEvent.Section.class);
    private static final ObjectArray<CollectDiagnosticsEvent.Section> RIGHT_SIDE_LAYOUT = new ObjectArray<>();
    private static final ObjectArray<CollectDiagnosticsEvent.Section> LEFT_SIDE_LAYOUT = new ObjectArray<>();

    private static final Supplier<List<Component>> SPECIAL_MODS_INSTALLED = Suppliers.memoize(() -> {
        var builder = ImmutableList.<Component>builder();
        for (var modId : Constants.SPECIAL_MODS)
            if (Platform.isModLoaded(modId))
                builder.add(Component.literal("MOD: " + modId).withStyle(SPECIAL_MOD_STYLE));
        return builder.build();
    });

    static {
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.Header, ColorPalette.PUMPKIN_ORANGE);
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.Systems, ColorPalette.GREEN);
        COLOR_MAP.put(CollectDiagnosticsEvent.Section.Particles, ColorPalette.HOT_PINK);
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
        LEFT_SIDE_LAYOUT.add(CollectDiagnosticsEvent.Section.Particles);
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

    private final LoggingTimerEMA diagnostics = new LoggingTimerEMA("Collect Diagnostic");
    private final LoggingTimerEMA rendering = new LoggingTimerEMA("Render Diagnostic");
    private final String branding;
    private final List<IDiagnosticPlugin> plugins;
    private final CollectDiagnosticsEvent reusableEvent = new CollectDiagnosticsEvent();
    private final ObjectArray<FormattedCharSequence> left = new ObjectArray<>(64);
    private final ObjectArray<FormattedCharSequence> right = new ObjectArray<>(64);
    private final ObjectArray<FormattedCharSequence> biomeText = new ObjectArray<>(64);
    private int displayDiagnostics;
    private boolean renderHud;

    public DiagnosticsOverlay(ModInformation modInformation) {
        var platformName = Platform.isFabric() ? "Fabric" : "NeoForge";
        this.branding = "%s (%s)".formatted(modInformation.getBranding(), platformName);
        this.displayDiagnostics = 0;

        this.plugins = ImmutableList.of(
                ContainerManager.resolve(ClientProfilerPlugin.class),
                ContainerManager.resolve(ViewerPlugin.class),
                ContainerManager.resolve(RuntimeDiagnosticsPlugin.class),
                ContainerManager.resolve(SoundEngineDiagnosticsPlugin.class)
        );
    }

    public void toggleCollection() {
        this.displayDiagnostics = MathStuff.wrap(this.displayDiagnostics + 1, 3);
    }

    @Override
    public void tick(Minecraft client) {
        this.diagnostics.begin();

        // We only want to take the processing hit if the debug overlay is activated
        this.renderHud = this.showDiagnosticHud();
        if (this.renderHud) {
            switch (this.displayDiagnostics) {
                case 1 -> this.tickDebugDiagnostic(client);
                case 2 -> this.tickBiomeDiagnostic(client);
            }
        }

        this.diagnostics.end();
    }

    private void tickBiomeDiagnostic(Minecraft client) {
        var player = GameUtils.getPlayer().orElseThrow();
        var biome = player.level().getBiome(player.getOnPos()).value();
        var info = ContainerManager.resolve(IBiomeLibrary.class).getBiomeInfo(biome);
        this.biomeText.clear();

        String fogColor = "No color";
        String fogDensity = "No density";
        if (info.getFogColor() != null) {
            fogColor = info.getFogColor().toString();
        }
        if (info.getFogDensity() != null) {
            fogDensity = info.getFogDensity().toString();
        }

        this.addToTextOutput("Biome: %s/%s".formatted(info.getBiomeName(), info.getBiomeId().toString()), BIOME_DIAGNOSTIC_HEADER_COLOR);
        this.addToTextOutput("Traits: %s".formatted(info.getTraits().toString()), BIOME_DIAGNOSTIC_HEADER_COLOR);
        this.addToTextOutput("Fog: %s/%s".formatted(fogColor, fogDensity), BIOME_DIAGNOSTIC_HEADER_COLOR);
        this.addToTextOutput(null, BIOME_DIAGNOSTIC_HEADER_COLOR);

        for (var soundType : SoundEventType.values()) {
            this.addToTextOutput(soundType.getName().toUpperCase(), BIOME_DIAGNOSTIC_TITLE_COLOR);
            var sounds = info.getSounds(soundType);
            if (sounds.isEmpty()) {
                this.addToTextOutput("Nothing", BIOME_DIAGNOSTIC_COLOR);
            } else {
                for (var sound : sounds) {
                    var style = sound.matches() ? BIOME_DIAGNOSTIC_MUSIC_ELIGIBLE : BIOME_DIAGNOSTIC_MUSIC_NOT_ELIGIBLE;
                    this.addToTextOutput(sound.toString(), style);
                }
            }
            this.addToTextOutput(null, BIOME_DIAGNOSTIC_COLOR);
        }

        this.addToTextOutput("COMMENTS", BIOME_DIAGNOSTIC_TITLE_COLOR);
        for (var comment : info.getComments()) {
            this.addToTextOutput(comment, BIOME_DIAGNOSTIC_COLOR);
        }
    }

    private void addToTextOutput(String text, Style style) {
        if (text != null) {
            this.biomeText.add(Component.literal(text).withStyle(style).getVisualOrderText());
        } else {
            this.biomeText.add(null);
        }
    }

    private void tickDebugDiagnostic(Minecraft client) {
        // Perform tick on the plugins
        this.plugins.forEach(p -> p.tick(client));

        this.reusableEvent.clear();
        this.reusableEvent.add(CollectDiagnosticsEvent.Section.Header, this.branding);

        var serverBrand = GameUtils.getServerBrand();
        serverBrand.ifPresent(brand -> this.reusableEvent.add(CollectDiagnosticsEvent.Section.Header, "Server Brand: %s".formatted(brand)));

        // Add any special mod indicators
        for (var mod : SPECIAL_MODS_INSTALLED.get())
            this.reusableEvent.add(CollectDiagnosticsEvent.Section.Header, mod);

        this.reusableEvent.add(this.diagnostics);
        this.reusableEvent.add(this.rendering);

        ClientEventHooks.COLLECT_DIAGNOSTICS_EVENT.invoker().onCollect(this.reusableEvent);

        this.left.clear();
        this.right.clear();

        processOutput(LEFT_SIDE_LAYOUT, this.reusableEvent, this.left);
        processOutput(RIGHT_SIDE_LAYOUT, this.reusableEvent, this.right);
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
    public void render(GuiGraphicsExtractor context, float partialTick) {
        this.rendering.begin();
        if (this.renderHud) {
            context.nextStratum();
            switch (this.displayDiagnostics) {
                case 1: {
                    this.drawText(context, this.left, true);
                    this.drawText(context, this.right, false);
                }
                break;
                case 2: {
                    this.drawText(context, this.biomeText, true);
                }
                break;
            }
        }
        this.rendering.end();
    }

    private boolean showDiagnosticHud() {
        return this.displayDiagnostics != 0 && GameUtils.isInGame() && !GameUtils.getMC().getDebugOverlay().showDebugScreen();
    }

    private void drawText(GuiGraphicsExtractor context, ObjectArray<FormattedCharSequence> text, boolean alignLeft) {
        var textRenderer = GameUtils.getTextRenderer();
        FormattedCharSequence component;
        int height = textRenderer.lineHeight;
        for (int i = 0; i < text.size(); ++i) {
            component = text.get(i);
            if (component == null)
                continue;
            int width = textRenderer.width(component);
            int left = alignLeft ? 2 : context.guiWidth() - 2 - width;
            int top = 2 + height * i;
            context.fill(left - 1, top - 1, left + width + 1, top + height - 1, BACKGROUND_COLOR);
            context.text(textRenderer, component, left, top, FOREGROUND_COLOR, false);
        }
    }
}
