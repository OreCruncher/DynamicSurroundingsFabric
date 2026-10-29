package org.orecruncher.dsurround.effects.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.ArrayDeque;

/**
 * The numbers that pop off an entity when its health changes (see {@link HealthPopoffEffect}): each jumps up, grows,
 * then shrinks and falls away over a little over half a second.
 * <p>
 * Not particles: each is a line of text drawn as a name tag is, facing the camera, outlined in black as glowing sign
 * text is, submitted with the level's features (from {@code MixinLevelRenderer}). All of them together are a handful
 * of quads in the font's batch. Where one is and how big are worked out from its age rather
 * than ticked, so they move smoothly at any frame rate. Client thread only.
 */
public final class HealthPopoffs {

    // Their life, in ticks
    static final float LIFE = 12F;
    // How fast they leave, and fall, in blocks a tick and a tick per tick
    static final float RISE = 0.12F;
    static final float GRAVITY = 0.032F;
    // How fast they drift sideways, at most, in blocks a tick: so several at once don't sit on each other
    private static final float DRIFT = 0.015F;
    // Size: blocks per pixel of the font at the start (a name tag's is 0.025), growing to PEAK times that by GROW
    // ticks, then shrinking to END times by the end of its life
    static final float SIZE = 0.024F;
    static final float PEAK = 3F;
    static final float GROW = 4F;
    static final float END = 1.2F;
    // The last part of its life it fades out over, fading to FADED (the text shader drops anything fainter than 0.1)
    static final float FADE = 0.25F;
    private static final float FADED = 0.15F;
    private static final int OUTLINE = 0xFF000000;
    // Full brightness, block and sky: they are labels, not lit by the world
    private static final int FULL_BRIGHT = 0xF000F0;
    // At most this many at once; past it the oldest go
    private static final int MAX = 64;
    private static final float NANOS_PER_TICK = 50_000_000F;

    private static final ArrayDeque<Popoff> POPOFFS = new ArrayDeque<>();

    private HealthPopoffs() {
    }

    private record Popoff(FormattedCharSequence text, float halfWidth, int color, double x, double y, double z,
                          float driftX, float driftZ, long born) {
    }

    /**
     * Pops {@code text} off from {@code (x, y, z)}.
     *
     * @param color as 0xRRGGBB
     */
    static void add(String text, int color, double x, double y, double z, float driftX, float driftZ) {
        if (POPOFFS.size() >= MAX)
            POPOFFS.pollFirst();
        var sequence = Component.literal(text).getVisualOrderText();
        float halfWidth = GameUtils.getTextRenderer().width(sequence) / 2F;
        POPOFFS.addLast(new Popoff(sequence, halfWidth, color & 0xFFFFFF, x, y, z,
                driftX * DRIFT, driftZ * DRIFT, System.nanoTime()));
    }

    /**
     * How many are showing, for diagnostics
     */
    public static int count() {
        return POPOFFS.size();
    }

    /**
     * Drops them all, as when leaving a world
     */
    public static void clear() {
        POPOFFS.clear();
    }

    /**
     * Submits them for drawing this frame, dropping those whose time is up
     */
    public static void submit(SubmitNodeCollector collector, CameraRenderState camera) {
        if (POPOFFS.isEmpty())
            return;

        final long now = System.nanoTime();
        while (!POPOFFS.isEmpty() && ageOf(POPOFFS.peekFirst(), now) >= LIFE)
            POPOFFS.pollFirst();

        var font = GameUtils.getMC().font;
        final float lineMiddle = font.lineHeight / 2F;
        double camX = camera.pos.x, camY = camera.pos.y, camZ = camera.pos.z;
        var poseStack = new PoseStack();
        for (var popoff : POPOFFS) {
            float age = ageOf(popoff, now);
            float scale = SIZE * sizeAt(age);
            int alpha = (int) (alphaAt(age) * 255F);

            poseStack.pushPose();
            poseStack.translate(
                    popoff.x + popoff.driftX * age - camX,
                    popoff.y + heightAt(age) - camY,
                    popoff.z + popoff.driftZ * age - camZ);
            poseStack.mulPose(camera.orientation);
            poseStack.scale(scale, -scale, scale);
            collector.submitText(poseStack, -popoff.halfWidth, -lineMiddle, popoff.text, false, Font.DisplayMode.NORMAL,
                    FULL_BRIGHT, (alpha << 24) | popoff.color, 0, (alpha << 24) | (OUTLINE & 0xFFFFFF));
            poseStack.popPose();
        }
    }

    private static float ageOf(Popoff popoff, long now) {
        return (now - popoff.born) / NANOS_PER_TICK;
    }

    /**
     * How far above where it started it is, in blocks, {@code age} ticks in: up, and falling back past the start
     */
    static float heightAt(float age) {
        return RISE * age - 0.5F * GRAVITY * age * age;
    }

    /**
     * Its size, as a multiple of {@link #SIZE}, {@code age} ticks in: growing quickly to {@link #PEAK}, then shrinking
     * to {@link #END}
     */
    static float sizeAt(float age) {
        if (age <= GROW)
            return Mth.lerp(Math.max(0F, age) / GROW, 1F, PEAK);
        float t = Math.min(1F, (age - GROW) / (LIFE - GROW));
        return PEAK * (float) Math.pow(END / PEAK, t);
    }

    /**
     * How opaque it is, {@code age} ticks in: solid, then fading over the last {@link #FADE} of its life
     */
    static float alphaAt(float age) {
        float fadeFrom = LIFE * (1F - FADE);
        if (age <= fadeFrom)
            return 1F;
        float t = Math.min(1F, (age - fadeFrom) / (LIFE - fadeFrom));
        return Mth.lerp(t, 1F, FADED);
    }
}
