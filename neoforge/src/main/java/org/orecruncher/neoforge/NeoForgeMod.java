package org.orecruncher.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.CustomEnvironmentEffectsRendererManager;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.DimensionInfo;
import org.orecruncher.dsurround.gui.overlay.OverlayManager;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.KnownMod;
import net.minecraft.world.phys.Vec3;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeMod {

    public NeoForgeMod(ModContainer container, IEventBus modBus) {
        modBus.addListener(this::onRegisterGuiLayersEvent);

        // A dimension with a weather renderer of its own keeps its weather (see DimensionInfo); looked up at the origin,
        // as the dimension's own. NeoForge takes it from an environment attribute, which a biome could set too: custom
        // weather in some biomes only would be missed, and particle weather drawn over it there (none known; checking
        // where the player is as the weather is drawn would catch it)
        DimensionInfo.drawsOwnWeather = level -> CustomEnvironmentEffectsRendererManager.getCustomWeatherEffectRenderer(level, Vec3.ZERO) != null;
        DimensionInfo.drawsOwnClouds = level -> CustomEnvironmentEffectsRendererManager.getCustomCloudsRenderer(level, Vec3.ZERO) != null;

        Client.initialize();
        Client.initializeClient();

        if (KnownMod.CLOTH_CONFIG.isInstalled())
            container.registerExtensionPoint(IConfigScreenFactory.class, new ModConfigMenu());

    }

    @SubscribeEvent
    public void onRegisterGuiLayersEvent(RegisterGuiLayersEvent event) {
        // Add the overlay manager to the render layers of Gui: above the vanilla HUD, as on Fabric
        OverlayManager overlayManager = ContainerManager.resolve(OverlayManager.class);
        event.registerAboveAll(Constants.asId("layer/overlaymanager"), overlayManager::render);
    }
}
