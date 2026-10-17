package org.orecruncher.dsurround.effects.frost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A side of a window that frosts: the outside of a glass block or pane, open in front. Where it is, in world
 * coordinates: the plane of the glass, lifted a little off it, and its extent across ({@code u}: x on a north or south
 * side, z on a west or east one) and up ({@code v}: y). The window's edges are found too, as far as
 * {@link FrostFaces#EDGE_REACH} beyond it: frost grows in from where the glass meets its frame, so a face in the middle
 * of a large window is bare when its edges are frosted.
 */
public final class FrostFace {

    final int x, y, z;
    final Direction side;
    final float plane;
    final float u0, u1, v0, v1;
    // Where the window this face is part of ends, across and up; at most EDGE_REACH beyond the face
    final float edgeU0, edgeU1, edgeV0, edgeV1;
    // Its packed light, and the sky light in front of it as a share of full, and the temperature there
    final int light;
    final float skyShare;
    final float temperature;
    // How much frost it should have, and how much it has, 0 to 1
    float target;
    float shown;

    FrostFace(int x, int y, int z, Direction side, float plane, float u0, float u1, float v0, float v1,
              float edgeU0, float edgeU1, float edgeV0, float edgeV1, int light, float skyShare, float temperature) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
        this.plane = plane;
        this.u0 = u0;
        this.u1 = u1;
        this.v0 = v0;
        this.v1 = v1;
        this.edgeU0 = edgeU0;
        this.edgeU1 = edgeU1;
        this.edgeV0 = edgeV0;
        this.edgeV1 = edgeV1;
        this.light = light;
        this.skyShare = skyShare;
        this.temperature = temperature;
    }

    /**
     * Which face this is, to carry its frost over when its section is looked at again: its block and side
     */
    long key() {
        return BlockPos.asLong(this.x, this.y, this.z) * 31L + this.side.get2DDataValue();
    }

    public float shown() {
        return this.shown;
    }

    /**
     * Gives it this much frost at once, 0 to 1, without growing or melting into it
     */
    public void show(float frost) {
        this.target = frost;
        this.shown = frost;
    }
}
