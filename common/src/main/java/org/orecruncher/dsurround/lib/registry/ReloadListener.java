package org.orecruncher.dsurround.lib.registry;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.eventing.IResourceReload;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;

public class ReloadListener implements ResourceManagerReloadListener {

    public ReloadListener() {
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        if (GameUtils.getMC().isSameThread()) {
            Library.LOGGER.info("ReloadListener - raising notification");
            IResourceReload.EVENT.invoker().onResourceReload(resourceManager);

            Library.LOGGER.info("ReloadListener - resetting configuration caches");
            var resourceUtilities = ResourceUtilities.createForResourceManager(resourceManager);
            IReloadEvent.EVENT.invoker().onReload(resourceUtilities, IReloadEvent.Scope.RESOURCES);
        }
    }
}
