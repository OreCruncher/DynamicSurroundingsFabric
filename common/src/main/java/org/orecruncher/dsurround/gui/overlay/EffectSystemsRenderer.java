package org.orecruncher.dsurround.gui.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.AABB;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.processing.AreaBlockEffects;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the block effects that the effect systems are tracking, in the world, for {@link DiagnosticsOverlay}'s
 * EFFECTS mode. Each effect's block gets a translucent box and an outline in its system's color, and above it a
 * label: the system's name, then what the system says about the effect. Labels show through walls, and only effects
 * near the camera get one; further away they are too small to read.
 * <p>
 * Called during world rendering, from vanilla's debug renderer (see MixinDebugRenderer), on the render thread.
 */
public final class EffectSystemsRenderer {

    private static final double LABEL_RANGE_SQ = 24 * 24;
    private static final float LABEL_SCALE = 0.02F;
    // Between label lines, in blocks: a little more than a line of text at LABEL_SCALE
    private static final double LABEL_LINE_SPACING = 0.25D;
    // Above the top of the block, in blocks
    private static final double LABEL_HEIGHT = 0.3D;
    // So the box shows over the block's own faces rather than flickering with them
    private static final double BOX_INFLATE = 0.005D;
    private static final float BOX_ALPHA = 0.25F;

    private static final List<String> LINES = new ArrayList<>();

    private EffectSystemsRenderer() {
    }

    // Resolved on first use, which is during world rendering, when everything is registered
    private static final class Services {
        static final DiagnosticsOverlay OVERLAY = ContainerManager.resolve(DiagnosticsOverlay.class);
        static final AreaBlockEffects AREA_BLOCK_EFFECTS = ContainerManager.resolve(AreaBlockEffects.class);
    }

    /**
     * Draws the tracked effects if the diagnostics overlay is showing them.
     *
     * @param camX the camera's position; the pose stack is relative to it
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, double camX, double camY, double camZ) {
        if (!GameUtils.isInGame() || !Services.OVERLAY.isShowingEffects())
            return;

        var area = Services.AREA_BLOCK_EFFECTS;

        // Each pass sticks to one kind of buffer: asking the buffer source for another kind can end the one in use

        // Translucent boxes (DebugRenderer works in world coordinates)
        area.forEachEffectSystem(system -> {
            float[] rgb = toRgb(system.getDiagnosticColor());
            system.forEachEffect(effect ->
                    DebugRenderer.renderFilledBox(poseStack, buffers, new AABB(effect.getPos()).inflate(BOX_INFLATE), rgb[0], rgb[1], rgb[2], BOX_ALPHA));
        });

        // Outlines (relative to the camera)
        var lines = buffers.getBuffer(RenderType.lines());
        area.forEachEffectSystem(system -> {
            float[] rgb = toRgb(system.getDiagnosticColor());
            system.forEachEffect(effect -> {
                var box = new AABB(effect.getPos()).inflate(BOX_INFLATE).move(-camX, -camY, -camZ);
                LevelRenderer.renderLineBox(poseStack, lines, box, rgb[0], rgb[1], rgb[2], 1F);
            });
        });

        // Labels, near the camera: the system's name on top, then its description of the effect
        area.forEachEffectSystem(system -> {
            int color = 0xFF000000 | system.getDiagnosticColor();
            system.forEachEffect(effect -> {
                var pos = effect.getPos();
                double x = pos.getX() + 0.5D;
                double z = pos.getZ() + 0.5D;
                if (pos.distToCenterSqr(camX, camY, camZ) > LABEL_RANGE_SQ)
                    return;

                LINES.clear();
                LINES.add(system.getName());
                system.describeEffect(effect, LINES::add);

                double y = pos.getY() + 1D + LABEL_HEIGHT + (LINES.size() - 1) * LABEL_LINE_SPACING;
                for (var text : LINES) {
                    DebugRenderer.renderFloatingText(poseStack, buffers, text, x, y, z, color, LABEL_SCALE, true, 0F, true);
                    y -= LABEL_LINE_SPACING;
                }
            });
        });
    }

    private static float[] toRgb(int color) {
        return new float[]{((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F};
    }
}
