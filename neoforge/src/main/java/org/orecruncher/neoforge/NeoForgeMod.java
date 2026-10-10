package org.orecruncher.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.orecruncher.dsurround.effects.ModShaders;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.gui.overlay.OverlayManager;
import org.orecruncher.dsurround.lib.di.ContainerManager;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeMod {

    public NeoForgeMod(ModContainer container, IEventBus modBus) {
        modBus.addListener(this::onRegisterGuiLayersEvent);
        modBus.addListener(this::onRegisterShaders);

        Client.initialize();
        Client.initializeClient();

        if (ModList.get().isLoaded(Constants.CLOTH_CONFIG_NEOFORGE))
            container.registerExtensionPoint(IConfigScreenFactory.class, new ModConfigMenu());

    }

    @SubscribeEvent
    public void onRegisterShaders(RegisterShadersEvent event) {
        // A shader that fails to compile throws here. Caught so the game still loads, without that shader.
        for (var definition : ModShaders.SHADERS) {
            try {
                var shader = new ShaderInstance(event.getResourceProvider(), definition.id(), definition.format());
                event.registerShader(shader, definition::onLoaded);
            } catch (Exception e) {
                definition.onFailed(e);
            }
        }
    }

    @SubscribeEvent
    public void onRegisterGuiLayersEvent(RegisterGuiLayersEvent event) {
        // Add the overlay manager to the render layers of Gui: above the vanilla HUD, as on Fabric
        OverlayManager overlayManager = ContainerManager.resolve(OverlayManager.class);
        event.registerAboveAll(Constants.asId("layer/overlaymanager"), overlayManager::render);
    }
}
