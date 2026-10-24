package org.orecruncher.dsurround.gui.overlay.lightlevel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.lib.di.ContainerManager;

/**
 * Draws {@link LightLevelOverlay}'s numbers in the world: a quad per block from the number's cell in
 * {@link LightLevelAtlas}, lying flat on the block and turned to read upright for the camera's heading, tinted by
 * the block's color. Sections out of view are skipped whole. The quads are opaque but for the soft edges of some
 * fonts, nudged toward the camera by a polygon offset (as sign text is) rather than lifted off the block.
 * <p>
 * Submitted with the world's other features each frame (see MixinLevelRenderer), with vanilla's text render type, so
 * the game draws them in a batch with its own; on the render thread.
 */
public final class LightLevelRenderer {

    // Space between the label and the block's edge, in blocks
    private static final float INSET = 0.1F;
    // Above the block's top, in blocks: little enough not to show, enough with the offset not to flicker into it
    private static final float LIFT = 1F / 256F;
    private static final float CELL = 1F / LightLevelAtlas.CELLS;
    private static final int FULL_BRIGHT = 0xF000F0;

    // Corners of a label in the order drawn, counter-clockwise seen from above: north west, south west, south
    // east, north east (LightLevels.cellCorner numbers them clockwise from north west)
    private static final int[] DRAW_ORDER = {0, 3, 2, 1};
    private static final float[] CORNER_U = new float[4];
    private static final float[] CORNER_V = new float[4];

    private LightLevelRenderer() {
    }

    // Resolved on first use, which is during world rendering, when everything is registered
    private static final class Services {
        static final LightLevelOverlay OVERLAY = ContainerManager.resolve(LightLevelOverlay.class);
    }

    /**
     * Submits the numbers if the overlay is on. Called outside any render pass, so the atlas can be drawn first if
     * the font has changed.
     */
    public static void submit(SubmitNodeCollector collector, CameraRenderState camera) {
        var overlay = Services.OVERLAY;
        if (!overlay.isOn())
            return;
        overlay.atlas().refresh();
        if (!overlay.isDrawing())
            return;

        var heading = Direction.fromYRot(camera.yRot);
        int turns = LightLevels.quarterTurns(heading.get2DDataValue());
        for (int corner = 0; corner < 4; corner++) {
            int cell = LightLevels.cellCorner(DRAW_ORDER[corner], turns);
            CORNER_U[corner] = cell == 1 || cell == 2 ? CELL : 0F;
            CORNER_V[corner] = cell >= 2 ? CELL : 0F;
        }

        var frustum = camera.cullFrustum;
        double camX = camera.pos.x, camY = camera.pos.y, camZ = camera.pos.z;
        collector.submitCustomGeometry(new PoseStack(), RenderTypes.textPolygonOffset(LightLevelAtlas.ID), (pose, consumer) -> {
            var matrix = pose.pose();
            var colors = overlay.colors();
            var values = overlay.values();
            int px = overlay.playerX(), py = overlay.playerY(), pz = overlay.playerZ(), range = overlay.range();
            int minY = py - range, maxY = py + LightLevelField.RANGE_UP;
            int[] drawn = {0};

            overlay.field().forEachSection(section -> {
                if (section.count == 0 || !frustum.isVisible(section.bounds))
                    return;
                int baseX = section.x << 4, baseY = section.y << 4, baseZ = section.z << 4;
                for (int i = 0; i < section.count; i++) {
                    int entry = section.entries[i];
                    int index = LightLevels.tableIndex(entry);
                    int color = colors[index];
                    if (color == 0)
                        continue;
                    // The range is a box around the player; the sections only roughly cover it
                    int x = baseX + LightLevels.localX(entry);
                    int y = baseY + LightLevels.localY(entry);
                    int z = baseZ + LightLevels.localZ(entry);
                    if (Math.abs(x - px) > range || Math.abs(z - pz) > range || y < minY || y > maxY)
                        continue;

                    int value = values[index];
                    float u = LightLevelAtlas.cellU(value);
                    float v = LightLevelAtlas.cellV(value);
                    float top = (float) (y + LightLevels.height(entry) + LIFT - camY);
                    float x0 = (float) (x + INSET - camX), x1 = (float) (x + 1 - INSET - camX);
                    float z0 = (float) (z + INSET - camZ), z1 = (float) (z + 1 - INSET - camZ);
                    vertex(consumer, matrix, x0, top, z0, color, u + CORNER_U[0], v + CORNER_V[0]);
                    vertex(consumer, matrix, x0, top, z1, color, u + CORNER_U[1], v + CORNER_V[1]);
                    vertex(consumer, matrix, x1, top, z1, color, u + CORNER_U[2], v + CORNER_V[2]);
                    vertex(consumer, matrix, x1, top, z0, color, u + CORNER_U[3], v + CORNER_V[3]);
                    drawn[0]++;
                }
            });
            overlay.setLabelsDrawn(drawn[0]);
        });
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, int color, float u, float v) {
        consumer.addVertex(pose, x, y, z).setColor(color).setUv(u, LightLevelAtlas.sampledV(v)).setLight(FULL_BRIGHT);
    }
}
