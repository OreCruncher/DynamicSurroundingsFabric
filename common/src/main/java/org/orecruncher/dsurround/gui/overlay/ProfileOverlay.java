package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.profiling.Profiler;

/**
 * Shows, at the top of the screen, that /dsprofile is about to record or is recording, with the time left: the player
 * is asked to keep still meanwhile, and the game may be slower for it.
 * <p>
 * It is drawn while a profile is recorded, so it must cost next to nothing, or it would show in the very profile it
 * marks: its text, laid out, is only made again when it changes, once a second. Made every frame, it was the mod's
 * largest cost in a recording.
 */
public class ProfileOverlay extends AbstractOverlay {

    // Opaque: amber while waiting to start, red while recording
    private static final int WAITING_COLOR = 0xFFFFC040;
    private static final int RECORDING_COLOR = 0xFFFF5050;
    // How far down the screen, in GUI pixels: clear of the top edge, above where boss bars are drawn
    private static final int TOP = 2;

    // What is shown now: for which state and seconds left, laid out, and how wide
    private Profiler.State shownState = Profiler.State.IDLE;
    private long shownSeconds = -1;
    @Nullable
    private FormattedCharSequence shown;
    private int shownWidth;

    @Override
    public void render(GuiGraphicsExtractor context, float partialTick) {
        var state = Profiler.state();
        if (state == Profiler.State.IDLE)
            return;
        var left = Profiler.secondsLeft();
        var font = GameUtils.getTextRenderer();
        if (this.shown == null || state != this.shownState || left != this.shownSeconds) {
            var time = (left / 60) + ":" + (left % 60 < 10 ? "0" : "") + (left % 60);
            var text = state == Profiler.State.WAITING
                    ? Component.translatable("dsurround.profile.overlay.waiting", time)
                    : Component.translatable("dsurround.profile.overlay.recording", time);
            this.shown = text.getVisualOrderText();
            this.shownWidth = font.width(this.shown);
            this.shownState = state;
            this.shownSeconds = left;
        }
        var color = state == Profiler.State.WAITING ? WAITING_COLOR : RECORDING_COLOR;
        context.text(font, this.shown, (context.guiWidth() - this.shownWidth) / 2, TOP, color);
    }
}
