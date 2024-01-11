package org.orecruncher.dsurround.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.tags.ItemEffectTags;

public class CompassOverlay extends AbstractOverlay {

    // Vertical offset to avoid writing over the cross-hair
    private static final int CROSSHAIR_OFFSET = 60;

    // Width and height of the actual band in the texture. The texture is 512x512 but the actual
    // rendering is smaller.
    private static final int TEXTURE_SIZE = 512;
    private static final float BAND_WIDTH = 65F * 2;
    private static final float BAND_HEIGHT = 12F * 2;
    private static final float TEXTURE_SIZE_F = (float)TEXTURE_SIZE;
    private static final int HALF_TEXTURE_SIZE = TEXTURE_SIZE / 2;
    private static final ResourceLocation COMPASS_TEXTURE = new ResourceLocation(Constants.MOD_ID, "textures/compass.png");

    private final ITagLibrary tagLibrary;
    private final Configuration config;
    private boolean showCompass;
    private float scale;
    private float spriteOffset;

    public CompassOverlay(Configuration config, ITagLibrary tagLibrary) {
        this.tagLibrary = tagLibrary;
        this.config = config;
        this.showCompass = false;
        this.spriteOffset = this.config.compassAndClockOptions.compassStyle.getSpriteNumber();
        this.scale = (float)this.config.compassAndClockOptions.scale;
    }

    public void tick(Minecraft client) {
        this.showCompass = false;

        if (this.config.compassAndClockOptions.enableCompass && GameUtils.isInGame()) {
            this.scale = (float) this.config.compassAndClockOptions.scale;
            this.spriteOffset = this.config.compassAndClockOptions.compassStyle.getSpriteNumber();

            var player = GameUtils.getPlayer().orElseThrow();
            var mainHandItem = player.getMainHandItem();
            var offHandItem = player.getOffhandItem();
            this.showCompass = this.doShowCompass(mainHandItem) || this.doShowCompass(offHandItem);
        }
    }

    private boolean doShowCompass(ItemStack stack) {
        return !stack.isEmpty() && this.tagLibrary.is(ItemEffectTags.COMPASSES, stack);
    }

    @Override
    public void render(GuiGraphics context, float partialTick) {
        if (!this.showCompass)
            return;

        var matrixStack = context.pose();

        try {

            matrixStack.pushPose();

            final var player = GameUtils.getPlayer().orElseThrow();

            int direction = Mth.floor(((player.getViewYRot(partialTick) * TEXTURE_SIZE) / 360F) + 0.5D) & (TEXTURE_SIZE - 1);
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

    public void drawTexture(PoseStack stack, ResourceLocation texture, float x, float y, float u, float v, float width, float height) {
        this.drawTexture(stack, texture, x, x + width, y, y + height, width, height, u, v);
    }

    void drawTexture(PoseStack stack, ResourceLocation texture, float x1, float x2, float y1, float y2, float regionWidth, float regionHeight, float u, float v) {
        this.drawTexturedQuad(stack, texture, x1, x2, y1, y2, (float) 0, u / TEXTURE_SIZE_F, (u + regionWidth) / TEXTURE_SIZE_F, v / TEXTURE_SIZE_F, (v + regionHeight) / TEXTURE_SIZE_F);
    }

    void drawTexturedQuad(PoseStack stack, ResourceLocation texture, float x1, float x2, float y1, float y2, float z, float u1, float u2, float v1, float v2) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
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
