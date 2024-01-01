package org.orecruncher.dsurround.gui.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.DayCycle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.tags.ItemEffectTags;

import java.util.EnumMap;
import java.util.Map;

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
    private static final ResourceLocation COMPASS_TEXTURE = new ResourceLocation(Constants.MOD_ID, "textures/compass.png");

    private static final Map<DayCycle, TextColor> COLOR_MAP = new EnumMap<>(DayCycle.class);

    static {
        COLOR_MAP.put(DayCycle.NO_SKY, ColorPalette.GOLD);
        COLOR_MAP.put(DayCycle.DAYTIME, ColorPalette.GOLD);
        COLOR_MAP.put(DayCycle.NIGHTTIME, ColorPalette.GREEN);
        COLOR_MAP.put(DayCycle.SUNRISE, ColorPalette.YELLOW);
        COLOR_MAP.put(DayCycle.SUNSET, ColorPalette.ORANGE);
    }

    private final ITagLibrary tagLibrary;
    private final Configuration config;
    private final MinecraftClock clock;
    private boolean showCompass;
    private boolean showClock;
    private float scale;
    private float spriteOffset;
    private String clockText;
    private TextColor clockColor;

    public CompassAndClockOverlay(Configuration config, ITagLibrary tagLibrary) {
        this.tagLibrary = tagLibrary;
        this.config = config;
        this.clock = new MinecraftClock();
        this.showCompass = false;
        this.showClock = false;
        this.spriteOffset = this.config.compassAndClockOptions.compassStyle.getSpriteNumber();
        this.scale = (float)this.config.compassAndClockOptions.scale;
    }

    public void tick(Minecraft client) {
        this.showClock = false;
        this.showCompass = false;

        this.scale = (float)this.config.compassAndClockOptions.scale;
        this.spriteOffset = this.config.compassAndClockOptions.compassStyle.getSpriteNumber();

        if (GameUtils.isInGame()) {
            var player = GameUtils.getPlayer().orElseThrow();
            var mainHandItem = player.getMainHandItem();
            var offHandItem = player.getOffhandItem();

            if (this.config.compassAndClockOptions.enableClock) {
                this.showClock = doShowClock(mainHandItem) || doShowClock(offHandItem);
                this.clock.update(player.level());
                this.clockText = this.clock.getFormattedTime();
                this.clockColor = COLOR_MAP.get(this.clock.getCycle());
            }

            if (this.config.compassAndClockOptions.enableCompass) {
                this.showCompass = doShowCompass(mainHandItem) || doShowCompass(offHandItem);
            }
        }
    }

    private boolean doShowClock(ItemStack stack) {
        return !stack.isEmpty() && this.tagLibrary.isIn(ItemEffectTags.CLOCKS, stack.getItem());
    }

    private boolean doShowCompass(ItemStack stack) {
        return !stack.isEmpty() && this.tagLibrary.isIn(ItemEffectTags.COMPASSES, stack.getItem());
    }

    @Override
    public void render(GuiGraphics context) {
        if (this.showCompass || this.showClock) {
            final var player = GameUtils.getPlayer().orElseThrow();

            var matrixStack = context.pose();

            if (this.showClock)
                try {
                    matrixStack.pushPose();
                    var textRender = GameUtils.getTextRenderer();

                    var width = textRender.width(this.clockText);
                    var height = textRender.lineHeight;
                    var adjustment = this.showCompass ? 6 : 1;

                    float x = (context.guiWidth() - width * this.scale) / 2F;
                    float y = (context.guiHeight() - CROSSHAIR_OFFSET - (height * adjustment) * this.scale) / 2F;

                    matrixStack.scale(this.scale, this.scale, 0F);
                    x /= this.scale;
                    y /= this.scale;

                    context.drawString(textRender, this.clockText, (int)x, (int)y, this.clockColor.getValue(), true);

                } finally {
                    matrixStack.popPose();
                }

            if (this.showCompass)
                try {

                    matrixStack.pushPose();

                    // TODO: Verify rotation for compass
                    int direction = Mth.floor(((player.yHeadRot * TEXTURE_SIZE) / 360F) + 0.5D) & (TEXTURE_SIZE - 1);
                    float x = (context.guiWidth() - BAND_WIDTH * this.scale) / 2F;
                    float y = (context.guiHeight() - CROSSHAIR_OFFSET - BAND_HEIGHT * this.scale) / 2F;

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
                    matrixStack.popPose();
                }
        }
    }

    public void drawTexture(PoseStack stack, ResourceLocation texture, float x, float y, float u, float v, float width, float height) {
        this.drawTexture(stack, texture, x, x + width, y, y + height, width, height, u, v);
    }

    void drawTexture(PoseStack stack, ResourceLocation texture, float x1, float x2, float y1, float y2, float regionWidth, float regionHeight, float u, float v) {
        this.drawTexturedQuad(stack, texture, x1, x2, y1, y2, (float) 0, u / TEXTURE_SIZE_F, (u + regionWidth) / TEXTURE_SIZE_F, v / TEXTURE_SIZE_F, (v + regionHeight) / TEXTURE_SIZE_F);
    }

    void drawTexturedQuad(PoseStack stack, ResourceLocation texture, float x1, float x2, float y1, float y2, float z, float u1, float u2, float v1, float v2) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        Matrix4f matrix4f = stack.last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix4f, x1, y1, z).uv(u1, v1).endVertex();
        bufferBuilder.vertex(matrix4f, x1, y2, z).uv(u1, v2).endVertex();
        bufferBuilder.vertex(matrix4f, x2, y2, z).uv(u2, v2).endVertex();
        bufferBuilder.vertex(matrix4f, x2, y1, z).uv(u2, v1).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
    }
}
