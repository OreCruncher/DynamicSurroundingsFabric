package org.orecruncher.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.Constants;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeMod {

    private final Client client;

    public NeoForgeMod(ModContainer container, IEventBus modBus) {
        modBus.addListener(this::onInitializeClient);

        this.client = new Client();
        this.client.construct();
        this.client.initializeClient();

        if (ModList.get().isLoaded(Constants.CLOTH_CONFIG_NEOFORGE))
            container.registerExtensionPoint(IConfigScreenFactory.class, new ModConfigMenu());
    }

    @SubscribeEvent
    public void onInitializeClient(FMLClientSetupEvent setupEvent) {
        // Boot the mod
        //this.client.initializeClient();
    }
}
