package org.orecruncher.neoforge;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.config.IConfigScreenFactoryProvider;
import org.orecruncher.dsurround.lib.config.IScreenFactory;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

public class ModConfigMenu implements IConfigScreenFactory {
    @Override
    public @NotNull Screen createScreen(@NotNull ModContainer modContainer, @NotNull Screen arg) {
        var factory = this.acquireFactory();
        if (factory != null)
            return factory.create(arg);
        return null;
    }

    private IScreenFactory<?> acquireFactory() {
        var logger = ContainerManager.resolve(IModLog.class);
        var provider = ContainerManager.resolve(IConfigScreenFactoryProvider.class);

        logger.info("NeoForge calling to get config screen");
        var factory = provider.getModConfigScreenFactory(Configuration.class);
        return factory.orElse(null);
    }
}
