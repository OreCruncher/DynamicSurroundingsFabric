package org.orecruncher.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.gui.overlay.OverlayManager;
import org.orecruncher.dsurround.lib.di.ContainerManager;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeMod {

    private final Client client;

    public NeoForgeMod(ModContainer container, IEventBus modBus) {
        modBus.addListener(this::onInitializeClient);
        modBus.addListener(this::onRegisterGuiLayersEvent);

        this.client = new Client();
        this.client.construct();
        this.client.initializeClient();

        if (ModList.get().isLoaded(Constants.CLOTH_CONFIG_NEOFORGE))
            container.registerExtensionPoint(IConfigScreenFactory.class, new ModConfigMenu());

    }

    @SubscribeEvent
    public void onRegisterGuiLayersEvent(RegisterGuiLayersEvent event) {
        // Add the overlay manager to the render layers of Gui
        OverlayManager overlayManager = ContainerManager.resolve(OverlayManager.class);
        event.registerBelowAll(Constants.asId("layer/overlaymanager"), overlayManager::render);
    }

    @SubscribeEvent
    public void onInitializeClient(FMLClientSetupEvent setupEvent) {
        // Boot the mod
        //this.client.initializeClient();
    }
}
