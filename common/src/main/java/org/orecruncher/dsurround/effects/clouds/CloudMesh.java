package org.orecruncher.dsurround.effects.clouds;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.runtime.weather.CloudCover;

/**
 * Turns the {@link CloudField} into quads, in two parts. The settled part: a top and a bottom for each column of cloud,
 * and its sides where the column next to it doesn't cover them. The changing part: where a cell's last change is still
 * fading, what is in only one of its columns, before or after (a whole cloud come or gone, or a step of one), as a box
 * fading in or out (see {@link #build}). Each quad is wound counter-clockwise seen from outside, so the inside of a
 * cloud isn't drawn.
 * <p>
 * A vertex's shade is how much of the light on the clouds it gets. Tops get all of it. A base gets less the thicker the
 * cloud above it, so a storm's deck is dark underneath and darkest under its towers; sides shade from their base's to
 * their top's, a little less on some sides than others, as vanilla's do. Thin high streaks are pale and let more through.
 * Positions are in blocks from the grid's corner, at the cloud height.
 */
public final class CloudMesh {

    /**
     * Where a quad's vertices go
     */
    public interface Sink {
        /**
         * @param shade     how much of the light on the clouds the vertex gets, 0 to 1
         * @param alpha     how much it hides what is behind it, 0 to 1
         * @param start     for the changing part, when its change began, in ticks from the field's last update (0 or
         *                  less); 0 for the settled part
         * @param direction 1 for the changing part fading in, -1 fading out; 0 for the settled part
         */
        void vertex(float x, float y, float z, float shade, float alpha, float start, float direction);
    }

    /**
     * The shade of a cloud's base: of a thin one, and of one {@link CloudCover#STORM_MAX} thick or more
     */
    static final float THIN_BASE = 0.72F;
    static final float THICK_BASE = 0.3F;
    // Sides facing east or west, and north or south, get this share of the light: vanilla's
    static final float X_SIDE = 0.9F;
    static final float Z_SIDE = 0.8F;
    // How much the low clouds hide: a thin one, and a thick one
    static final float THIN_ALPHA = 0.8F;
    static final float THICK_ALPHA = 0.95F;
    // The high streaks: their shade underneath, and how much they hide
    static final float HIGH_BASE = 0.9F;
    static final float HIGH_ALPHA = 0.45F;
    // The thickness at which a cloud is as dark underneath as it gets
    private static final float DARKEST_AT = 40F;

    private CloudMesh() {
    }

    /**
     * Hands each quad of the clouds to a sink, four vertices at a time: the settled part to one, the changing part to
     * another
     *
     * @return how many quads, of both
     */
    public static int build(CloudField field, Sink settled, Sink changing) {
        // One of each, set for each column and piece in turn: built every few ticks, for every cell
        var look = new Look();
        var box = new Box();
        int quads = 0;
        for (var layer : CloudField.Layer.values())
            for (int j = 0; j < field.size(); j++)
                for (int i = 0; i < field.size(); i++)
                    quads += cell(field, layer, i, j, settled, changing, look, box);
        return quads;
    }

    private static int cell(CloudField field, CloudField.Layer layer, int i, int j, Sink settled, Sink changing,
                            Look look, Box box) {
        int base = field.base(layer, i, j);
        int top = field.top(layer, i, j);
        boolean fading = field.isFading(i, j);
        int prevBase = fading ? field.previousBase(layer, i, j) : base;
        int prevTop = fading ? field.previousTop(layer, i, j) : top;
        boolean now = top > base;
        boolean before = prevTop > prevBase;
        if (!now && !before)
            return 0;

        // Shaded as the whole of the cloud there, before and after
        int shadeBase = !now ? prevBase : !before ? base : Math.min(base, prevBase);
        int shadeTop = !now ? prevTop : !before ? top : Math.max(top, prevTop);
        look.set(layer, shadeBase, shadeTop);
        float x0 = i * CloudField.CELL;
        float z0 = j * CloudField.CELL;
        int quads = 0;

        // What is in it before and after
        int sBase = field.settledBase(layer, i, j);
        int sTop = field.settledTop(layer, i, j);
        boolean anySettled = sTop > sBase;
        if (anySettled) {
            box.set(settled, x0, z0, sBase, sTop, look, 0F, 0F);
            quads += box.top() + box.bottom();
            // Each side, where the neighbor's settled column leaves it open: below the neighbor's base and above its top
            quads += side(field, layer, i + 1, j, box, Face.EAST);
            quads += side(field, layer, i - 1, j, box, Face.WEST);
            quads += side(field, layer, i, j + 1, box, Face.SOUTH);
            quads += side(field, layer, i, j - 1, box, Face.NORTH);
        }
        if (!fading)
            return quads;

        // What is only in one of them, fading in or out
        float start = (float) (field.changeTick(i, j) - field.tick());
        if (now)
            quads += pieces(changing, box, x0, z0, base, top, before, prevBase, prevTop, anySettled, sBase, sTop, look, start, 1F);
        if (before)
            quads += pieces(changing, box, x0, z0, prevBase, prevTop, now, base, top, anySettled, sBase, sTop, look, start, -1F);
        return quads;
    }

    /**
     * The parts of a column [from, to] that aren't in another [otherFrom, otherTo], each a box: none of its faces
     * against the settled part
     */
    private static int pieces(Sink sink, Box box, float x0, float z0, int from, int to, boolean other, int otherFrom,
                              int otherTo, boolean anySettled, int sBase, int sTop, Look look, float start, float direction) {
        int quads = 0;
        if (!other) {
            quads += piece(sink, box, x0, z0, from, to, anySettled, sBase, sTop, look, start, direction);
            return quads;
        }
        if (from < otherFrom)
            quads += piece(sink, box, x0, z0, from, Math.min(to, otherFrom), anySettled, sBase, sTop, look, start, direction);
        if (to > otherTo)
            quads += piece(sink, box, x0, z0, Math.max(from, otherTo), to, anySettled, sBase, sTop, look, start, direction);
        return quads;
    }

    private static int piece(Sink sink, Box box, float x0, float z0, int y0, int y1, boolean anySettled, int sBase,
                             int sTop, Look look, float start, float direction) {
        if (y1 <= y0)
            return 0;
        box.set(sink, x0, z0, y0, y1, look, start, direction);
        int quads = 0;
        if (!anySettled || y1 != sBase)
            quads += box.top();
        if (!anySettled || y0 != sTop)
            quads += box.bottom();
        for (var face : Face.ALL)
            quads += box.side(face, y0, y1);
        return quads;
    }

    /**
     * The open parts of one side of the settled part of a column
     */
    private static int side(CloudField field, CloudField.Layer layer, int ni, int nj, Box box, Face face) {
        int nBase = field.settledBase(layer, ni, nj);
        int nTop = field.settledTop(layer, ni, nj);
        if (nTop <= nBase)
            return box.side(face, box.y0, box.y1);
        int quads = 0;
        if (nBase > box.y0)
            quads += box.side(face, box.y0, Math.min(box.y1, nBase));
        if (nTop < box.y1)
            quads += box.side(face, Math.max(box.y0, nTop), box.y1);
        return quads;
    }

    /**
     * How a column is shaded: its base's shade, and how much it hides; sides shade from its base's to all the light at
     * its top. Set for each column in turn.
     */
    private static final class Look {
        private float baseShade;
        private float alpha;
        private int base;
        private int top;

        void set(CloudField.Layer layer, int base, int top) {
            this.base = base;
            this.top = top;
            if (layer == CloudField.Layer.HIGH) {
                this.baseShade = HIGH_BASE;
                this.alpha = HIGH_ALPHA;
                return;
            }
            float thick = Mth.clamp((top - base - CloudCover.FLAT) / (DARKEST_AT - CloudCover.FLAT), 0F, 1F);
            this.baseShade = Mth.lerp(thick, THIN_BASE, THICK_BASE);
            this.alpha = Mth.lerp(thick, THIN_ALPHA, THICK_ALPHA);
        }

        /**
         * A side's shade at a height: its base's at the bottom, all the light at the top
         */
        float shadeAt(float y) {
            float up = this.top > this.base ? Mth.clamp((y - this.base) / (this.top - this.base), 0F, 1F) : 1F;
            return Mth.lerp(up, this.baseShade, 1F);
        }
    }

    private enum Face {
        EAST, WEST, SOUTH, NORTH;

        // values() copies its array each call, and every changing piece goes round them
        static final Face[] ALL = values();
    }

    /**
     * A box of cloud filling a cell from y0 to y1, its faces handed to a sink. Set for each box in turn.
     */
    private static final class Box {
        private Sink sink;
        private float x0;
        private float z0;
        private int y0;
        private int y1;
        private Look look;
        private float start;
        private float direction;

        void set(Sink sink, float x0, float z0, int y0, int y1, Look look, float start, float direction) {
            this.sink = sink;
            this.x0 = x0;
            this.z0 = z0;
            this.y0 = y0;
            this.y1 = y1;
            this.look = look;
            this.start = start;
            this.direction = direction;
        }

        float x1() {
            return this.x0 + CloudField.CELL;
        }

        float z1() {
            return this.z0 + CloudField.CELL;
        }

        int top() {
            float a = this.look.alpha;
            float s = this.look.shadeAt(this.y1);
            this.vertex(this.x0, this.y1, this.z0, s, a);
            this.vertex(this.x0, this.y1, this.z1(), s, a);
            this.vertex(this.x1(), this.y1, this.z1(), s, a);
            this.vertex(this.x1(), this.y1, this.z0, s, a);
            return 1;
        }

        int bottom() {
            float a = this.look.alpha;
            float s = this.look.shadeAt(this.y0);
            this.vertex(this.x0, this.y0, this.z0, s, a);
            this.vertex(this.x1(), this.y0, this.z0, s, a);
            this.vertex(this.x1(), this.y0, this.z1(), s, a);
            this.vertex(this.x0, this.y0, this.z1(), s, a);
            return 1;
        }

        /**
         * One side, from y0 to y1
         */
        int side(Face face, float y0, float y1) {
            float factor = face == Face.EAST || face == Face.WEST ? X_SIDE : Z_SIDE;
            float s0 = this.look.shadeAt(y0) * factor;
            float s1 = this.look.shadeAt(y1) * factor;
            float a = this.look.alpha;
            switch (face) {
                case EAST -> {
                    this.vertex(this.x1(), y0, this.z0, s0, a);
                    this.vertex(this.x1(), y1, this.z0, s1, a);
                    this.vertex(this.x1(), y1, this.z1(), s1, a);
                    this.vertex(this.x1(), y0, this.z1(), s0, a);
                }
                case WEST -> {
                    this.vertex(this.x0, y0, this.z0, s0, a);
                    this.vertex(this.x0, y0, this.z1(), s0, a);
                    this.vertex(this.x0, y1, this.z1(), s1, a);
                    this.vertex(this.x0, y1, this.z0, s1, a);
                }
                case SOUTH -> {
                    this.vertex(this.x0, y0, this.z1(), s0, a);
                    this.vertex(this.x1(), y0, this.z1(), s0, a);
                    this.vertex(this.x1(), y1, this.z1(), s1, a);
                    this.vertex(this.x0, y1, this.z1(), s1, a);
                }
                case NORTH -> {
                    this.vertex(this.x0, y0, this.z0, s0, a);
                    this.vertex(this.x0, y1, this.z0, s1, a);
                    this.vertex(this.x1(), y1, this.z0, s1, a);
                    this.vertex(this.x1(), y0, this.z0, s0, a);
                }
            }
            return 1;
        }

        private void vertex(float x, float y, float z, float shade, float alpha) {
            this.sink.vertex(x, y, z, shade, alpha, this.start, this.direction);
        }
    }
}
