package org.orecruncher.dsurround.eventing;

import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Fired when the sky has been drawn, before anything else in the world. Things drawn now appear behind the world,
 * as part of the sky.
 */
@GenerateInvoker
@FunctionalInterface
public interface ISkyRender {

    IPhasedEvent<ISkyRender> EVENT = EventingFactory.createPrioritizedEvent(ISkyRenderInvoker::create);

    /**
     * @param frustumMatrix the camera's rotation (the sky is drawn around the camera, so there is no translation)
     * @param partialTick   how far into the current tick
     * @param camera        the camera
     * @param isFoggy       whether the sky is hidden by thick fog (vanilla skips drawing it then)
     */
    void onRenderSky(Matrix4f frustumMatrix, float partialTick, Camera camera, boolean isFoggy);
}
