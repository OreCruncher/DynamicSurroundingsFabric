package org.orecruncher.dsurround.tags;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import org.orecruncher.dsurround.Constants;

import java.util.Collection;
import java.util.HashSet;

public class ParticleTags {

    static final Collection<TagKey<ParticleType<?>>> TAGS = new HashSet<>();

    /**
     * Particles of other mods and vanilla that drift with the wind: smoke, ash, leaves. The mod's own particles
     * handle the wind themselves, so are never listed.
     */
    public static final TagKey<ParticleType<?>> WIND_BLOWN = of("wind_blown");

    private static TagKey<ParticleType<?>> of(String id) {
        var tagKey = TagKey.create(Registries.PARTICLE_TYPE, Constants.asId("effects/" + id));
        TAGS.add(tagKey);
        return tagKey;
    }
}
