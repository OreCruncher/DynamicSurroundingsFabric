package org.orecruncher.dsurround.gui.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.DayCycle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.tags.ItemEffectTags;

import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class CompassAndClockOverlay extends AbstractOverlay {

    // Vertical offset to avoid writing over the cross-hair
    private static final int CROSSHAIR_OFFSET = 60;

    // Width and height of the actual band in the texture.  The texture if a 512x512 but the actual texture
    // is smaller.
    private static final int TEXTURE_SIZE = 512;
    private static final float BAND_WIDTH = 65F * 2;
    private static final float BAND_HEIGHT = 12F * 2;
    private static final float TEXTURE_SIZE_F = (float)TEXTURE_SIZE;
    private static final int HALF_TEXTURE_SIZE = TEXTURE_SIZE / 2;
    private static final Identifier COMPASS_TEXTURE = new Identifier(Client.ModId, "textures/compass.png");

    private static final Map<DayCycle, TextColor> COLOR_MAP = new EnumMap<>(DayCycle.class);

    static {
        COLOR_MAP.put(DayCycle.NO_SKY, ColorPalette.GOLD);
        COLOR_MAP.put(DayCycle.DAYTIME, ColorPalette.GOLD);
        COLOR_MAP.put(DayCycle.NIGHTTIME, ColorPalette.GREEN);
        COLOR_MAP.put(DayCycle.SUNRISE, ColorPalette.YELLOW);
        COLOR_MAP.put(DayCycle.SUNSET, ColorPalette.ORANGE);
    }

    private final Configuration config;
    private final MinecraftClock clock;
    private boolean showCompass;
    private boolean showClock;
    private float scale;
    private float spriteOffset;
    private String clockText;
    private TextColor clockColor;

    public CompassAndClockOverlay(Configuration config) {
        this.config = config;
        this.clock = new MinecraftClock();
        this.showCompass = false;
        this.showClock = false;
        this.spriteOffset = this.config.compassAndClockOptions.compassStyle.getSpriteNumber();
        this.scale = (float)this.config.compassAndClockOptions.scale;
    }

    public void tick(MinecraftClient client) {
        this.showClock = false;
        this.showCompass = false;

        this.scale = (float)this.config.compassAndClockOptions.scale;
        this.spriteOffset = this.config.compassAndClockOptions.compassStyle.getSpriteNumber();

        if (GameUtils.isInGame()) {
            var player = GameUtils.getPlayer();
            var mainHandItem = player.getMainHandStack();
            var offHandItem = player.getOffHandStack();

            if (this.config.compassAndClockOptions.enableClock) {
                this.showClock = mainHandItem.isIn(ItemEffectTags.CLOCKS) || offHandItem.isIn(ItemEffectTags.CLOCKS);
                this.clock.update(player.getEntityWorld());
                this.clockText = this.clock.getFormattedTime();
                this.clockColor = COLOR_MAP.get(this.clock.getCycle());
            }

            if (this.config.compassAndClockOptions.enableCompass) {
                this.showCompass = mainHandItem.isIn(ItemEffectTags.COMPASSES) || offHandItem.isIn(ItemEffectTags.COMPASSES);
            }
        }
    }

    @Override
    public void render(DrawContext context) {
        if (this.showCompass || this.showClock) {
            final var player = GameUtils.getPlayer();

            var matrixStack = context.getMatrices();

            if (this.showClock)
                try {
                    matrixStack.push();
                    var textRender = GameUtils.getTextRenderer();

                    var width = textRender.getWidth(this.clockText);
                    var height = textRender.fontHeight;
                    var adjustment = this.showCompass ? 6 : 1;

                    float x = (context.getScaledWindowWidth() - width * this.scale) / 2F;
                    float y = (context.getScaledWindowHeight() - CROSSHAIR_OFFSET - (height * adjustment) * this.scale) / 2F;

                    matrixStack.scale(this.scale, this.scale, 0F);
                    x /= this.scale;
                    y /= this.scale;

                    context.drawText(textRender, this.clockText, (int)x, (int)y, this.clockColor.getRgb(), true);

                } finally {
                    matrixStack.pop();
                }

            if (this.showCompass)
                try {

                    matrixStack.push();

                    int direction = MathHelper.floor(((player.headYaw * TEXTURE_SIZE) / 360F) + 0.5D) & (TEXTURE_SIZE - 1);
                    float x = (context.getScaledWindowWidth() - BAND_WIDTH * this.scale) / 2F;
                    float y = (context.getScaledWindowHeight() - CROSSHAIR_OFFSET - BAND_HEIGHT * this.scale) / 2F;

                    matrixStack.scale(this.scale, this.scale, 0F);
                    x /= this.scale;
                    y /= this.scale;

                    float v = this.spriteOffset * (BAND_HEIGHT * 2);

                    if (direction >= HALF_TEXTURE_SIZE) {
                        direction -= HALF_TEXTURE_SIZE;
                        v += BAND_HEIGHT;
                    }

                    this.drawTexture(matrixStack, COMPASS_TEXTURE, x, y, direction, v, BAND_WIDTH, BAND_HEIGHT);

                } finally {
                    matrixStack.pop();
                }
        }
    }

    public void drawTexture(MatrixStack stack, Identifier texture, float x, float y, float u, float v, float width, float height) {
        this.drawTexture(stack, texture, x, x + width, y, y + height, width, height, u, v);
    }

    void drawTexture(MatrixStack stack, Identifier texture, float x1, float x2, float y1, float y2, float regionWidth, float regionHeight, float u, float v) {
        this.drawTexturedQuad(stack, texture, x1, x2, y1, y2, (float) 0, u / TEXTURE_SIZE_F, (u + regionWidth) / TEXTURE_SIZE_F, v / TEXTURE_SIZE_F, (v + regionHeight) / TEXTURE_SIZE_F);
    }

    void drawTexturedQuad(MatrixStack stack, Identifier texture, float x1, float x2, float y1, float y2, float z, float u1, float u2, float v1, float v2) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        Matrix4f matrix4f = stack.peek().getPositionMatrix();
        BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        bufferBuilder.vertex(matrix4f, x1, y1, z).texture(u1, v1).next();
        bufferBuilder.vertex(matrix4f, x1, y2, z).texture(u1, v2).next();
        bufferBuilder.vertex(matrix4f, x2, y2, z).texture(u2, v2).next();
        bufferBuilder.vertex(matrix4f, x2, y1, z).texture(u2, v1).next();
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }
}
