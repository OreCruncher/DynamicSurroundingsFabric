package org.orecruncher.dsurround.lib.gui;

import net.minecraft.client.Minecraft;

/**
 * Holds a tooltip back until the mouse has rested on what it belongs to for a moment, so tooltips don't flash up
 * while the mouse is passing over a list or dragging a slider. Asked each frame the mouse is over the thing: one per
 * config entry on the Cloth Config screen, and one per row hovered on the individual sound screen.
 * <p>
 * A hover starts when it is asked again after not being asked for a while (the mouse came back, or a list scrolled
 * the thing under a still mouse). Moving the mouse or holding the left button starts the wait over.
 */
public final class TooltipDelay {

    /**
     * How long the mouse must rest on an entry before its tooltip shows
     */
    static final long DELAY_NANOS = 500_000_000L;
    /**
     * Longer than a frame, even a slow one: asked again after a gap this long, the mouse has been away
     */
    static final long HOVER_GAP_NANOS = 150_000_000L;

    private boolean asked;
    private long lastAsked;
    private long restingSince;
    private double lastX;
    private double lastY;

    /**
     * Whether the tooltip should show now, going by the game's mouse
     */
    public boolean ready() {
        var mouse = Minecraft.getInstance().mouseHandler;
        return this.ready(System.nanoTime(), mouse.xpos(), mouse.ypos(), mouse.isLeftPressed());
    }

    /**
     * Whether the tooltip should show now
     *
     * @param now        the time, from {@link System#nanoTime()}
     * @param mouseX     the mouse's position
     * @param mouseY     the mouse's position
     * @param buttonDown whether the left button is held (dragging a slider, or about to click)
     */
    public boolean ready(long now, double mouseX, double mouseY, boolean buttonDown) {
        boolean newHover = !this.asked || now - this.lastAsked > HOVER_GAP_NANOS;
        if (newHover || buttonDown || mouseX != this.lastX || mouseY != this.lastY)
            this.restingSince = now;
        this.asked = true;
        this.lastAsked = now;
        this.lastX = mouseX;
        this.lastY = mouseY;
        return now - this.restingSince >= DELAY_NANOS;
    }
}
