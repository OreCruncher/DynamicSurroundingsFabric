package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Sprites from the particle atlas, without a registered particle type. The vanilla atlas definition stitches every
 * {@code textures/particle} image of every namespace, so the mod's sprites are there without the particle engine
 * knowing about them. Sprites are looked up on each call: the atlas replaces them when resources reload.
 */
public final class AtlasSpriteSet implements SpriteSet {

    private final List<Identifier> ids;
    // The sprites as of the last call to sprites(); a reload of the atlas replaces them all
    @Nullable
    private TextureAtlasSprite[] cached;

    private AtlasSpriteSet(List<Identifier> ids) {
        this.ids = ids;
    }

    /**
     * @param names sprite names under the mod's {@code textures/particle}, in animation order
     */
    public static AtlasSpriteSet of(String... names) {
        return new AtlasSpriteSet(Arrays.stream(names).map(Constants::asId).toList());
    }

    public List<Identifier> ids() {
        return this.ids;
    }

    @Override
    public @NotNull TextureAtlasSprite get(int age, int lifetime) {
        // Same frame selection as the particle engine's sprite sets
        return sprite(this.ids.get(age * (this.ids.size() - 1) / lifetime));
    }

    /**
     * Every sprite in the set, in order, as the atlas has them now. The array is kept and given out again until the
     * atlas is reloaded, which is noticed by its first sprite having changed, so it must not be changed by callers.
     * Render thread only.
     */
    public TextureAtlasSprite[] sprites() {
        var first = sprite(this.ids.getFirst());
        var current = this.cached;
        if (current != null && current[0] == first)
            return current;
        var result = new TextureAtlasSprite[this.ids.size()];
        result[0] = first;
        for (int i = 1; i < result.length; i++)
            result[i] = sprite(this.ids.get(i));
        this.cached = result;
        return result;
    }

    @Override
    public @NotNull TextureAtlasSprite get(RandomSource random) {
        return sprite(this.ids.get(random.nextInt(this.ids.size())));
    }

    @Override
    public @NotNull TextureAtlasSprite first() {
        return sprite(this.ids.getFirst());
    }

    private static TextureAtlasSprite sprite(Identifier id) {
        return GameUtils.getMC().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(id);
    }
}
