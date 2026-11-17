// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.metadata.animation.VillagerMetadataSection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * Who an apparition was: the villager it is the ghost of, drawn as the game draws one, the villager texture with its
 * biome's clothes over it and its profession's over those. The biome is where it started (a swamp's ghost wears a
 * swamp villager's clothes), the profession comes from its seed, so every player sees the same one. Any profession can
 * come up, a mod's too, or none at all, but one whose texture is missing is drawn as none.
 *
 * @param type       its biome's clothes
 * @param profession its profession's clothes, or null for none
 * @param typeHat    whether its biome's hat shows: not under a profession's hat, as the game decides it
 */
public record ApparitionLook(Identifier type, @Nullable Identifier profession, boolean typeHat) {

    public static final Identifier BASE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    static final Identifier PLAINS = texture("type", VillagerType.PLAINS);
    public static final ApparitionLook PLAIN = new ApparitionLook(PLAINS, null, true);
    private static final long SALT = 0x70726F66L;

    /**
     * How one started in a biome with a seed looks, with the textures the game has now
     */
    public static ApparitionLook of(Level level, Biome biome, long seed) {
        var resources = Minecraft.getInstance().getResourceManager();
        var holder = level.registryAccess().lookupOrThrow(Registries.BIOME).wrapAsHolder(biome);
        return of(resources, VillagerType.byBiome(holder), profession(seed));
    }

    static ApparitionLook of(ResourceProvider resources, ResourceKey<VillagerType> typeKey, ResourceKey<VillagerProfession> professionKey) {
        var type = texture("type", typeKey);
        var typeResource = resources.getResource(type).orElse(null);
        if (typeResource == null) {
            type = PLAINS;
            typeResource = resources.getResource(type).orElse(null);
        }
        Identifier profession = null;
        var professionHat = VillagerMetadataSection.Hat.NONE;
        if (!professionKey.equals(VillagerProfession.NONE)) {
            var id = texture("profession", professionKey);
            var resource = resources.getResource(id).orElse(null);
            if (resource != null) {
                profession = id;
                professionHat = hat(resource);
            }
        }
        return new ApparitionLook(type, profession, typeHatShows(hat(typeResource), professionHat));
    }

    /**
     * The profession one with a seed had: any of them, none included, alike
     */
    static ResourceKey<VillagerProfession> profession(long seed) {
        List<ResourceKey<VillagerProfession>> keys = BuiltInRegistries.VILLAGER_PROFESSION.registryKeySet().stream()
                .sorted(Comparator.comparing(ResourceKey::identifier)).toList();
        if (keys.isEmpty())
            return VillagerProfession.NONE;
        return keys.get(Randomizer.create(seed ^ SALT).nextInt(keys.size()));
    }

    /**
     * Whether a biome's hat shows with a profession's, as the game's villager layer decides: under no profession hat,
     * or under a partial one unless the biome's is a full one
     */
    static boolean typeHatShows(VillagerMetadataSection.Hat type, VillagerMetadataSection.Hat profession) {
        return profession == VillagerMetadataSection.Hat.NONE
                || (profession == VillagerMetadataSection.Hat.PARTIAL && type != VillagerMetadataSection.Hat.FULL);
    }

    static Identifier texture(String kind, ResourceKey<?> key) {
        return key.identifier().withPath(path -> "textures/entity/villager/" + kind + "/" + path + ".png");
    }

    private static VillagerMetadataSection.Hat hat(@Nullable Resource resource) {
        if (resource == null)
            return VillagerMetadataSection.Hat.NONE;
        try {
            return resource.metadata().getSection(VillagerMetadataSection.TYPE)
                    .map(VillagerMetadataSection::hat).orElse(VillagerMetadataSection.Hat.NONE);
        } catch (IOException ignored) {
            return VillagerMetadataSection.Hat.NONE;
        }
    }
}
