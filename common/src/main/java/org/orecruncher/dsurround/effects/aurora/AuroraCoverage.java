package org.orecruncher.dsurround.effects.aurora;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * How much of the screen the aurora's quads cover, each counted as often as it is drawn over (with additive blending
 * and no depth test, each band's pixels run the shader whatever is in front), for the recording /dsprofile is making:
 * what the aurora costs on the GPU follows it. Each quad is split into the two triangles it is drawn as and cut to the
 * screen and to in front of the camera. Render thread only.
 */
public final class AuroraCoverage {

    // In front of the camera: how far a corner's w must be past zero
    private static final float NEAR_W = 1.0E-4F;
    // A triangle cut by five planes has at most eight corners
    private static final int MOST_CORNERS = 8;

    private final Matrix4f clip = new Matrix4f();
    private final Vector4f corner = new Vector4f();
    // Clip-space x, y and w of the triangle as it is cut down, and of what each cut leaves
    private final float[] xs = new float[MOST_CORNERS];
    private final float[] ys = new float[MOST_CORNERS];
    private final float[] ws = new float[MOST_CORNERS];
    private final float[] cutX = new float[MOST_CORNERS];
    private final float[] cutY = new float[MOST_CORNERS];
    private final float[] cutW = new float[MOST_CORNERS];
    // Covered, in normalized device coordinates (the screen is 4)
    private double area;

    /**
     * Starts a frame's count.
     *
     * @param clip from the quads' coordinates to clip space
     */
    public void start(Matrix4fc clip) {
        this.clip.set(clip);
        this.area = 0;
    }

    /**
     * Adds a quad, its corners in the order they are drawn
     */
    public void quad(float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3) {
        this.triangle(x0, y0, z0, x1, y1, z1, x2, y2, z2);
        this.triangle(x2, y2, z2, x3, y3, z3, x0, y0, z0);
    }

    /**
     * The pixels covered since {@link #start}, on a screen this size; more than the screen's where quads overlap
     */
    public double pixels(int width, int height) {
        return this.area * width * height / 4.0;
    }

    private void triangle(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2) {
        this.load(0, x0, y0, z0);
        this.load(1, x1, y1, z1);
        this.load(2, x2, y2, z2);
        int n = 3;
        // In front of the camera first, so the screen's edges are only cut where w is positive
        n = this.cut(n, 0F, 0F, 1F, NEAR_W);
        n = this.cut(n, 1F, 0F, 1F, 0F);
        n = this.cut(n, -1F, 0F, 1F, 0F);
        n = this.cut(n, 0F, 1F, 1F, 0F);
        n = this.cut(n, 0F, -1F, 1F, 0F);
        if (n < 3)
            return;

        double twice = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double xi = this.xs[i] / this.ws[i], yi = this.ys[i] / this.ws[i];
            double xj = this.xs[j] / this.ws[j], yj = this.ys[j] / this.ws[j];
            twice += xi * yj - xj * yi;
        }
        this.area += Math.abs(twice) / 2.0;
    }

    private void load(int i, float x, float y, float z) {
        this.clip.transform(this.corner.set(x, y, z, 1F));
        this.xs[i] = this.corner.x;
        this.ys[i] = this.corner.y;
        this.ws[i] = this.corner.w;
    }

    /**
     * Cuts the polygon to where {@code a x + b y + c w >= offset}, returning how many corners are left
     */
    private int cut(int n, float a, float b, float c, float offset) {
        int m = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float di = a * this.xs[i] + b * this.ys[i] + c * this.ws[i] - offset;
            float dj = a * this.xs[j] + b * this.ys[j] + c * this.ws[j] - offset;
            if (di >= 0F) {
                this.cutX[m] = this.xs[i];
                this.cutY[m] = this.ys[i];
                this.cutW[m] = this.ws[i];
                m++;
            }
            if ((di >= 0F) != (dj >= 0F)) {
                float t = di / (di - dj);
                this.cutX[m] = this.xs[i] + t * (this.xs[j] - this.xs[i]);
                this.cutY[m] = this.ys[i] + t * (this.ys[j] - this.ys[i]);
                this.cutW[m] = this.ws[i] + t * (this.ws[j] - this.ws[i]);
                m++;
            }
        }
        System.arraycopy(this.cutX, 0, this.xs, 0, m);
        System.arraycopy(this.cutY, 0, this.ys, 0, m);
        System.arraycopy(this.cutW, 0, this.ws, 0, m);
        return m;
    }
}
