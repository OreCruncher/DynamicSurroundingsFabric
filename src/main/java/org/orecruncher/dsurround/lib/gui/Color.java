package org.orecruncher.dsurround.lib.gui;

import com.google.common.base.Preconditions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.Vector3d;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.orecruncher.dsurround.lib.Utilities;

@SuppressWarnings("unused")
@Environment(EnvType.CLIENT)
public class Color {

    protected float red;
    protected float green;
    protected float blue;
    protected float alpha;

    public Color(final String fmt) {
        final String[] parts = fmt.split(",");
        Preconditions.checkArgument(parts.length > 2);

        final int r = Integer.getInteger(parts[0]);
        final int g = Integer.getInteger(parts[1]);
        final int b = Integer.getInteger(parts[2]);
        final int a = parts.length == 4 ? Integer.getInteger(parts[3]) : 255;

        this.red = MathHelper.clamp(r / 255F, 0, 1F);
        this.green = MathHelper.clamp(g / 255F, 0, 1F);
        this.blue = MathHelper.clamp(b / 255F, 0, 1F);
        this.alpha = MathHelper.clamp(a / 255F, 0, 1F);
    }

    public Color(final Formatting fmt) {
        Preconditions.checkArgument(fmt.isColor());
        Preconditions.checkNotNull(fmt.getColorValue());

        final int color = fmt.getColorValue();
        this.red = ((color >> 16) & 0xff) / 255F;
        this.green = ((color >> 8) & 0xff) / 255F;
        this.blue = (color & 0xff) / 255F;
        this.alpha = 1F;
    }

    public Color(final Color color) {
        this(color.red, color.green, color.blue, color.alpha);
    }

    public Color(final int red, final int green, final int blue) {
        this(red, green, blue, 255);
    }

    public Color(final int red, final int green, final int blue, final int alpha) {
        this(red / 255F, green / 255F, blue / 255F, alpha / 255F);
    }

    public Color(final Vector3d vec) {
        this((float) vec.x, (float) vec.y, (float) vec.z);
    }

    public Color(final int rgb) {
        this((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, (rgb >> 24) & 0xff);
    }

    public Color(final float red, final float green, final float blue) {
        this(red, green, blue, 1F);
    }

    public Color(final float red, final float green, final float blue, final float alpha) {
        this.red = MathHelper.clamp(red, 0, 1F);
        this.green = MathHelper.clamp(green, 0, 1F);
        this.blue = MathHelper.clamp(blue, 0, 1F);
        this.alpha = MathHelper.clamp(alpha, 0, 1F);
    }

    public Color(final double red, final double green, final double blue, final double alpha) {
        this((float) red, (float) green, (float) blue, (float) alpha);
    }

    public static Color parse(final String input) {
        if (input.startsWith("#")) {
            return new Color(Integer.parseInt(input.substring(1), 16));
        }

        int[] parts = Utilities.splitToInts(input, ',');

        if (parts.length < 3) {
            throw new IllegalArgumentException(String.format("'%s' is not a valid color definition", input));
        }

        return new Color(
                MathHelper.clamp(parts[0], 0, 255),
                MathHelper.clamp(parts[1], 0, 255),
                MathHelper.clamp(parts[2], 0, 255)
        );
    }

    protected static float blnd(final float c1, final float c2, final float factor) {
        return MathHelper.clamp((float) Math.sqrt((1.0F - factor) * c1 * c1 + factor * c2 * c2), 0, 1F);
    }

    public float red() {
        return this.red;
    }

    public float green() {
        return this.green;
    }

    public float blue() {
        return this.blue;
    }

    public Vector3d toVec3d() {
        return new Vector3d(this.red, this.green, this.blue);
    }

    /*
     * Calculates the RGB adjustments to make to the color to arrive at the target
     * color after the specified number of iterations.
     */
    public Vector3d transitionTo(final Color target, final int iterations) {
        final double deltaRed = (target.red - this.red) / iterations;
        final double deltaGreen = (target.green - this.green) / iterations;
        final double deltaBlue = (target.blue - this.blue) / iterations;
        return new Vector3d(deltaRed, deltaGreen, deltaBlue);
    }

    public Color scale(final float scaleFactor) {
        return scale(scaleFactor, scaleFactor, scaleFactor);
    }

    public Color scale(final float scaleRed, final float scaleGreen, final float scaleBlue) {
        return new Color(this.red * scaleRed, this.green * scaleGreen, this.blue * scaleBlue, this.alpha);
    }

    public Color add(final Color color) {
        return add(color.red, color.green, color.blue);
    }

    public Color add(final float red, final float green, final float blue) {
        return new Color(this.red + red, this.green + green, this.blue + blue, this.alpha);
    }

    public Color blend(final Color color, final float factor) {
        return new Color(
                blnd(this.red, color.red, factor),
                blnd(this.green, color.green, factor),
                blnd(this.blue, color.blue, factor),
                this.alpha);
    }

    public Color mix( final Color color) {
        return mix(color.red, color.green, color.blue);
    }

    public Color mix(final float red, final float green, final float blue) {
        return new Color(
                (this.red + red) / 2.0F,
                (this.green + green) / 2.0F,
                (this.blue + blue) / 2.0F,
                this.alpha);
    }

    // Adjust luminance based on the specified percent. > 0 brightens; < 0
    // darkens
    public Color luminance(final float percent) {
        final float r = MathHelper.clamp(this.red + (this.red * percent), 0, 1F);
        final float g = MathHelper.clamp(this.green + (this.green * percent), 0, 1F);
        final float b = MathHelper.clamp(this.blue + (this.blue * percent), 0, 1F);
        return new Color(r, g, b, this.alpha);
    }

    public int rgb() {
        final int iRed = (int) (this.red * 255);
        final int iGreen = (int) (this.green * 255);
        final int iBlue = (int) (this.blue * 255);
        final int iAlpha = (int) (this.alpha * 255);
        return iAlpha << 24 | iRed << 16 | iGreen << 8 | iBlue;
    }

    public int rgbWithAlpha(final float alpha) {
        return rgbWithAlpha((int)(alpha * 255));
    }

    public int rgbWithAlpha(final int alpha) {
        final int iRed = (int) (this.red * 255);
        final int iGreen = (int) (this.green * 255);
        final int iBlue = (int) (this.blue * 255);
        final int iAlpha = (int) (this.alpha * 255);
        return alpha * 255 << 24 | iRed << 16 | iGreen << 8 | iBlue;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(this.red);
        result = 31 * result + Float.hashCode(this.green);
        result = 31 * result + Float.hashCode(this.blue);
        result = 31 * result + Float.hashCode(this.alpha);
        return result;
    }

    @Override
    public boolean equals(final Object anObject) {
        if (!(anObject instanceof Color))
            return false;
        final Color color = (Color) anObject;
        return this.red == color.red && this.green == color.green && this.blue == color.blue && this.alpha == this.alpha;
    }

    
    public MutableColor asMutable() {
        return new MutableColor(this);
    }

    @Override
    public String toString() {
        return "[r:" + (int) (this.red * 255) +
                ",g:" + (int) (this.green * 255) +
                ",b:" + (int) (this.blue * 255) +
                ",a:" + (int) (this.alpha * 255) +
                ']';
    }

    public static final class MutableColor extends Color {

        MutableColor( final Color color) {
            super(color);
        }

        @Override
        public Color add(final float red, final float green, final float blue) {
            this.red = MathHelper.clamp(this.red + red, 0, 1F);
            this.green = MathHelper.clamp(this.green + green, 0, 1F);
            this.blue = MathHelper.clamp(this.blue + blue, 0, 1F);
            return this;
        }

        
        @Override
        public Color blend( final Color color, final float factor) {
            this.red = blnd(this.red, color.red, factor);
            this.green = blnd(this.green, color.green, factor);
            this.blue = blnd(this.blue, color.blue, factor);
            return this;
        }

        
        @Override
        public Color scale(final float scaleRed, final float scaleGreen, final float scaleBlue) {
            this.red = MathHelper.clamp(this.red * scaleRed, 0, 1F);
            this.green = MathHelper.clamp(this.green * scaleGreen, 0, 1F);
            this.blue = MathHelper.clamp(this.blue * scaleBlue, 0, 1F);
            return this;
        }

        
        @Override
        public Color mix(final float red, final float green, final float blue) {
            this.red = MathHelper.clamp((this.red + red) / 2.0F, 0, 1F);
            this.green = MathHelper.clamp((this.green + green) / 2.0F, 0, 1F);
            this.blue = MathHelper.clamp((this.blue + blue) / 2.0F, 0, 1F);
            return this;
        }

        
        public Color adjust( final Vector3d adjust,  final Color target) {
            this.red += adjust.x;
            if ((adjust.x < 0.0F && this.red < target.red) || (adjust.x > 0.0F && this.red > target.red)) {
                this.red = target.red;
            }

            this.green += adjust.y;
            if ((adjust.y < 0.0F && this.green < target.green) || (adjust.y > 0.0F && this.green > target.green)) {
                this.green = target.green;
            }

            this.blue += adjust.z;
            if ((adjust.z < 0.0F && this.blue < target.blue) || (adjust.z > 0.0F && this.blue > target.blue)) {
                this.blue = target.blue;
            }
            return this;
        }

        
        public Color asImmutable() {
            return new Color(this);
        }
    }
}