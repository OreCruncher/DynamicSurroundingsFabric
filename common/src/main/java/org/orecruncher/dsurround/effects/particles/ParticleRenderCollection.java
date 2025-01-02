package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.lang.ref.WeakReference;
import java.util.function.Supplier;

/**
 * Special particle that proxies a collection in the particle engine. The commonality
 * of the collection is rendering setup. This collection is centered on the player
 * to prevent it from going out of scope. It is modeled on the NoRenderParticle in
 * Minecraft.
 */
public final class ParticleRenderCollection<TParticle extends TextureSheetParticle> extends Particle {

    private final Supplier<ResourceLocation> textureSupplier;
    private final ObjectArray<TParticle> particles;

    private ParticleRenderCollection(ClientLevel clientLevel, Supplier<ResourceLocation> textureSupplier) {
        super(clientLevel, 0, 0, 0);
        this.textureSupplier = textureSupplier;
        this.particles = new ObjectArray<>(128);
        this.tick();
    }

    @NotNull
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    @Override
    public void tick() {
        // Keep the particle colocated with the player
        var playerPos = GameUtils.getPlayer().orElseThrow().getEyePosition();
        this.setPos(playerPos.x(), playerPos.y(), playerPos.z());

        if (!this.particles.isEmpty()) {
            this.particles.forEach(Particle::tick);
            this.particles.removeIf(p -> !p.isAlive());
        }
    }

    @Override
    public void render(@NotNull VertexConsumer vertexConsumer, @NotNull Camera camera, float tickDelta) {
        if (this.particles.isEmpty())
            return;

        RenderSystem.setShaderTexture(0, this.textureSupplier.get());
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        this.particles.forEach(p -> p.render(vertexConsumer, camera, tickDelta));
    }

    public void add(TParticle particle) {
        // Can only accept custom style particles
        if (particle.getRenderType() != this.getRenderType())
            throw new RuntimeException("Can only add render type %s particles to collection".formatted(this.getRenderType()));
        this.particles.add(particle);
    }

    public static final class Helper<TParticle extends TextureSheetParticle> {

        private final Supplier<ResourceLocation> textureSupplier;
        private WeakReference<ParticleRenderCollection<TParticle>> particle;

        public Helper(Supplier<ResourceLocation> textureSupplier) {
            this.textureSupplier = textureSupplier;
        }

        public ParticleRenderCollection<TParticle> get() {
            var pc = this.particle != null ? this.particle.get() : null;
            if (pc == null || !pc.isAlive()) {
                pc = new ParticleRenderCollection<>(GameUtils.getWorld().orElseThrow(), this.textureSupplier);
                this.particle = new WeakReference<>(pc);
                GameUtils.getParticleManager().add(pc);
            }
            return pc;
        }

        public void clear() {
            var pc = this.particle != null ? this.particle.get() : null;
            if (pc != null) {
                pc.remove();
                this.particle = null;
            }
        }
    }
}
