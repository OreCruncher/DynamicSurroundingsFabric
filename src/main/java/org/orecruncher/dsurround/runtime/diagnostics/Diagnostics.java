package org.orecruncher.dsurround.runtime.diagnostics;

import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.infra.ModInformation;
import org.orecruncher.dsurround.lib.infra.events.ClientState;
import org.orecruncher.dsurround.lib.math.LoggingTimerEMA;

@Environment(EnvType.CLIENT)
public final class Diagnostics {

    private final LoggingTimerEMA diagnostics = new LoggingTimerEMA("Diagnostics");
    private final String branding;

    private boolean enableCollection = false;
    private ObjectArray<String> left = new ObjectArray<>();
    private ObjectArray<String> right = new ObjectArray<>();

    public Diagnostics(ModInformation modInfo) {
        this.branding = modInfo.get_branding();

        // Forces registration/creation of the various diagnostics plugins.
        // They should self register to the diagnostics collection event.
        ContainerManager.resolve(ClientProfiler.class);
        ContainerManager.resolve(BlockViewer.class);
        ContainerManager.resolve(RuntimeDiagnostics.class);
        ContainerManager.resolve(SoundEngineDiagnostics.class);

        ClientState.TICK_END.register(this::tick);
    }

    public void toggleCollection() {
        this.enableCollection = !this.enableCollection;
    }

    public boolean isCollecting() {
        return this.enableCollection;
    }

    /**
     * Called by a mixin hook to obtain information for rendering in the diagnostic HUD
     */
    public ObjectArray<String> getLeft() {
        return this.left;
    }

    /**
     * Called by a mixin hook to obtain information for rendering in the diagnostic HUD
     */
    public ObjectArray<String> getRight() {
        return this.right;
    }

    private void tick(MinecraftClient client) {
        if (this.enableCollection && GameUtils.isInGame()) {
            this.diagnostics.begin();

            var event = new ClientEventHooks.CollectDiagnosticsEvent();

            event.left.add(this.branding);

            ClientEventHooks.COLLECT_DIAGNOSTICS.raise(event);

            event.timers.add(diagnostics);

            this.left = event.left;
            this.right = new ObjectArray<>(event.right.size() + event.timers.size() + 1);

            for (var timer : event.timers)
                this.right.add(Formatting.LIGHT_PURPLE + timer.toString());

            this.right.add(Strings.EMPTY);
            this.right.addAll(event.right);

            this.diagnostics.end();
        }
    }
}
