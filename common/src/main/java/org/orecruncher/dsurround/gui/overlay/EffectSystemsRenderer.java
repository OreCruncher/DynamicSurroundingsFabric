package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
 * Emitted as gizmos each frame, along with vanilla's debug renderers' (see MixinDebugRenderer), on the render
 * thread.
 */
public final class EffectSystemsRenderer {

    private static final double LABEL_RANGE_SQ = 24 * 24;
    private static final float LABEL_SCALE = TextGizmo.Style.DEFAULT_SCALE;
    // Between label lines, in blocks: a little more than a line of text at LABEL_SCALE
    private static final double LABEL_LINE_SPACING = 0.25D;
    // Above the top of the block, in blocks
    private static final double LABEL_HEIGHT = 0.3D;
    // So the box shows over the block's own faces rather than flickering with them
    private static final double BOX_INFLATE = 0.005D;
    private static final int BOX_ALPHA = 0x40;
    private static final float OUTLINE_WIDTH = 1F;

    private static final List<String> LINES = new ArrayList<>();

    private EffectSystemsRenderer() {
    }

    // Resolved on first use, which is during world rendering, when everything is registered
    private static final class Services {
        static final DiagnosticsOverlay OVERLAY = ContainerManager.resolve(DiagnosticsOverlay.class);
        static final AreaBlockEffects AREA_BLOCK_EFFECTS = ContainerManager.resolve(AreaBlockEffects.class);
    }

    /**
     * Emits the tracked effects if the diagnostics overlay is showing them.
     *
     * @param camX the camera's position, for choosing which effects get a label
     */
    public static void emit(double camX, double camY, double camZ) {
        if (!GameUtils.isInGame() || !Services.OVERLAY.isShowingEffects())
            return;

        Services.AREA_BLOCK_EFFECTS.forEachEffectSystem(system -> {
            int rgb = system.getDiagnosticColor() & 0xFFFFFF;
            var style = GizmoStyle.strokeAndFill(0xFF000000 | rgb, OUTLINE_WIDTH, (BOX_ALPHA << 24) | rgb);
            var textStyle = TextGizmo.Style.forColorAndCentered(0xFF000000 | rgb).withScale(LABEL_SCALE);
            system.forEachEffect(effect -> {
                var pos = effect.getPos();
                Gizmos.cuboid(new AABB(pos).inflate(BOX_INFLATE), style);

                // Labels near the camera: the system's name on top, then its description of the effect
                if (pos.distToCenterSqr(camX, camY, camZ) > LABEL_RANGE_SQ)
                    return;

                LINES.clear();
                LINES.add(system.getName());
                system.describeEffect(effect, LINES::add);

                double x = pos.getX() + 0.5D;
                double z = pos.getZ() + 0.5D;
                double y = pos.getY() + 1D + LABEL_HEIGHT + (LINES.size() - 1) * LABEL_LINE_SPACING;
                for (var text : LINES) {
                    Gizmos.billboardText(text, new Vec3(x, y, z), textStyle).setAlwaysOnTop();
                    y -= LABEL_LINE_SPACING;
                }
            });
        });
    }
}
