package org.orecruncher.dsurround.eventing;

import org.joml.Matrix4f;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Fired when an Overworld-like sky has been drawn (sun, moon and stars), before anything else in the world. Things
 * drawn now appear behind the world, as part of the sky.
 */
@GenerateInvoker
@FunctionalInterface
public interface ISkyRender {

    IPhasedEvent<ISkyRender> EVENT = EventingFactory.createPrioritizedEvent(ISkyRenderInvoker::create);

    /**
     * @param viewMatrix      the camera's rotation (the sky is drawn around the camera, so there is no translation)
     * @param partialTick     how far into the current tick
     * @param starBrightness  how bright the stars were drawn, 0 to 1
     * @param rainBrightness  how much the rain lets through, 1 when clear
     */
    void onRenderSky(Matrix4f viewMatrix, float partialTick, float starBrightness, float rainBrightness);
}
