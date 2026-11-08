// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.MeshDraw;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.QuadWriter;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.lib.Library;

import java.util.List;
import java.util.Optional;

/**
 * Draws apparitions: the game's villager model, posed by us (where it faces, where its head turns), written into a
 * {@link QuadWriter} through a small {@link VertexConsumer} of our own, so no entity is involved. Pale and see-through,
 * white going to blue, from the villager texture's brightness alone (a resource pack's villager is used), shaded a
 * little by which way each face looks, and fading out from the waist down to nothing at the knees.
 * <p>
 * Two passes, as the clouds have: the figure's depth first, where it shows enough, then its color against that depth,
 * so only its nearest faces are seen and parts behind one another (an arm over the body) don't pile up. Drawn in the
 * default {@code IWorldRender} phase, after the weather; off while a shader pack is in use.
 */
public final class ApparitionRenderer {

    private static final String SAMPLER = "Sampler0";
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    // How see-through it is at most, and how much a face's direction shades it
    static final float OPACITY = 0.62F;
    private static final float SHADE = 0.3F;
    // As the game places a living thing's model: up the model's own height, flipped
    private static final float MODEL_OFFSET = 1.501F;

    private static final BindGroupLayout SAMPLER_LAYOUT = BindGroupLayout.builder().withUniform(SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER).build();
    private static final ColorTargetState NO_COLOR = new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE);
    private static final DepthStencilState WRITE_DEPTH = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true);
    private static final DepthStencilState TEST_DEPTH = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);

    private static final RenderPipeline DEPTH_PIPELINE = pipeline("pipeline/apparition_depth", true)
            .withColorTargetState(NO_COLOR).withDepthStencilState(WRITE_DEPTH).build();
    private static final RenderPipeline COLOR_PIPELINE = pipeline("pipeline/apparition", false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)).withDepthStencilState(TEST_DEPTH).build();

    public static final ModShader DEPTH = new ModShader(DEPTH_PIPELINE, "apparitions", "apparitions will not be drawn", Library.LOGGER);
    public static final ModShader COLOR = new ModShader(COLOR_PIPELINE, "apparitions", "apparitions will not be drawn", Library.LOGGER);

    /**
     * All of them, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(DEPTH, COLOR);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Apparitions");

    private static final MeshDraw DEPTH_DRAW = new MeshDraw("apparition depth");
    private static final MeshDraw COLOR_DRAW = new MeshDraw("apparitions");
    private static final ModelWriter WRITER = new ModelWriter();
    @Nullable
    private static QuadWriter quads;
    // The villager model, made from the models the game has now: made again when they are reloaded
    @Nullable
    private static ModelPart model;
    @Nullable
    private static EntityModelSet madeFrom;

    private ApparitionRenderer() {
    }

    private static RenderPipeline.Builder pipeline(String location, boolean depth) {
        var builder = RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId("core/apparition"))
                .withFragmentShader(Constants.asId("core/apparition"))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withBindGroupLayout(SAMPLER_LAYOUT)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                // The model's faces are wound for culling, as the game draws it
                .withCull(true);
        if (depth)
            builder.withShaderDefine("DEPTH_ONLY");
        return builder;
    }

    /**
     * Whether they can be drawn: their pipelines compiled, drawing hasn't failed, and no shader pack is in use
     */
    public static boolean isUsable() {
        return DEPTH.isUsable() && COLOR.isUsable();
    }

    /**
     * Draws them, outside any render pass, to the main target
     */
    public static void draw(List<Apparition.Pose> poses, Camera camera, RenderTarget target) {
        if (poses.isEmpty() || !isUsable())
            return;
        RENDER_TIMER.begin();
        RENDER_TIMER.count(poses.size());
        DEPTH.run(() -> {
            var written = write(poses, camera);
            if (written.vertexCount() == 0)
                return;
            var texture = Minecraft.getInstance().getTextureManager().getTexture(TEXTURE);
            var view = texture.getTextureView();
            var sampler = texture.getSampler();
            DEPTH_DRAW.draw(written, DEPTH_PIPELINE, target, pass -> pass.setUniform(SAMPLER, view, sampler));
            COLOR.run(() -> COLOR_DRAW.draw(written, COLOR_PIPELINE, target, pass -> pass.setUniform(SAMPLER, view, sampler)), () -> {});
        }, () -> {});
        RENDER_TIMER.end();
    }

    private static QuadWriter write(List<Apparition.Pose> poses, Camera camera) {
        if (quads == null)
            quads = new QuadWriter(QuadWriter.Format.POSITION_TEX_COLOR);
        quads.begin(48 * poses.size());
        var root = model();
        var head = root.getChild("head");
        var position = camera.position();
        var stack = new PoseStack();
        for (var pose : poses) {
            if (pose.alpha() <= 0F)
                continue;
            float fx = (float) (pose.x() - position.x), fy = (float) (pose.y() - position.y), fz = (float) (pose.z() - position.z);
            stack.pushPose();
            stack.translate(fx, fy, fz);
            stack.rotateDegrees(Axis.YP, 180F - pose.bodyYaw());
            stack.scale(-1F, -1F, 1F);
            stack.translate(0F, -MODEL_OFFSET, 0F);
            head.yRot = pose.headYaw() * Mth.DEG_TO_RAD;
            head.xRot = pose.headPitch() * Mth.DEG_TO_RAD;
            WRITER.start(quads, fy, pose.alpha());
            root.render(stack, WRITER, 0, OverlayTexture.NO_OVERLAY);
            stack.popPose();
        }
        return quads;
    }

    private static ModelPart model() {
        var models = Minecraft.getInstance().getEntityModels();
        if (model == null || madeFrom != models) {
            model = models.bakeLayer(ModelLayers.VILLAGER);
            madeFrom = models;
        }
        return model;
    }

    /**
     * Takes the corners the model hands over, as the game's vertex writers would, and writes them as our quads, with
     * the fade and shading in their color. The model hands over every corner with all it has in one call; the other
     * calls are never made by it, and do nothing.
     */
    private static final class ModelWriter implements VertexConsumer {

        private QuadWriter out;
        private float feetY;
        private float alpha;

        void start(QuadWriter out, float feetY, float alpha) {
            this.out = out;
            this.feetY = feetY;
            this.alpha = alpha * OPACITY;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            float shade = 1F - SHADE + SHADE * (0.5F + 0.5F * normalY);
            this.out.vertex(x, y, z, u, v, QuadWriter.color(shade, shade, shade, this.alpha * Apparition.fade(y - this.feetY)));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
