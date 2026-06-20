package org.orecruncher.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.config.IConfigScreenFactoryProvider;
import org.orecruncher.dsurround.lib.config.IScreenFactory;
import org.orecruncher.dsurround.lib.config.compat.ClothAPIFactoryProvider;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

/**
 * Hook for ModMenu to get a hold of our configuration screen.
 */
public class ModConfigMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> {
            try {
                var factory = this.acquireFactory();
                if (factory != null) {
                    return factory.create(screen);
                }
            } catch (Throwable t) {
                this.logger().error(t, "Unable to create ModMenu config screen");
            }
            return new MissingClothConfigScreen(screen);
        };
    }

    private IScreenFactory<?> acquireFactory() {
        var logger = this.logger();
        var provider = this.provider();

        logger.info("ModMenu calling to get config screen");
        var factory = provider.getModConfigScreenFactory(Configuration.class);
        return factory.orElse(null);
    }

    private IConfigScreenFactoryProvider provider() {
        try {
            return ContainerManager.resolve(IConfigScreenFactoryProvider.class);
        } catch (Throwable t) {
            // Mod Menu can query its entrypoint before the normal client initializer has finished
            // registering services. Build the Cloth factory provider directly in that early path.
            return new ClothAPIFactoryProvider();
        }
    }

    private IModLog logger() {
        try {
            return ContainerManager.resolve(IModLog.class);
        } catch (Throwable t) {
            return Library.LOGGER;
        }
    }

    private static final class MissingClothConfigScreen extends Screen {
        private static final Component TITLE = Component.literal("Dynamic Surroundings Configuration");
        private static final Component MESSAGE = Component.literal("The settings screen could not be created.");
        private static final Component HINT = Component.literal("Install/enable Cloth Config for Minecraft 26.2, then reopen Mod Menu.");

        private final Screen parent;

        private MissingClothConfigScreen(Screen parent) {
            super(TITLE);
            this.parent = parent;
        }

        @Override
        protected void init() {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.closeToParent())
                    .size(200, 20)
                    .pos(this.width / 2 - 100, this.height / 2 + 32)
                    .build());
        }

        @Override
        public void onClose() {
            this.closeToParent();
        }

        private void closeToParent() {
            if (this.minecraft != null) {
                GameUtils.setScreen(this.parent);
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
            super.extractRenderState(context, mouseX, mouseY, partialTick);
            context.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 42, 0xFFFFFF);
            context.centeredText(this.font, MESSAGE, this.width / 2, this.height / 2 - 12, 0xFFFFFF);
            context.centeredText(this.font, HINT, this.width / 2, this.height / 2 + 8, 0xA0A0A0);
        }
    }
}
