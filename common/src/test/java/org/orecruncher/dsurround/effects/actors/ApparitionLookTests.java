// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.SharedConstants;
import net.minecraft.client.resources.metadata.animation.VillagerMetadataSection;
import net.minecraft.client.resources.metadata.animation.VillagerMetadataSection.Hat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for who an apparition was: which villager's textures it takes, and which hat shows.
 */
public class ApparitionLookTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Resource texture(Hat hat) {
        return new Resource(null, InputStream::nullInputStream, hat == Hat.NONE ? () -> ResourceMetadata.EMPTY
                : () -> ResourceMetadata.of(VillagerMetadataSection.TYPE, new VillagerMetadataSection(hat)));
    }

    // The game's textures as they are: the hats in their .mcmeta files
    private static final Map<Identifier, Resource> VANILLA = Map.of(
            ApparitionLook.texture("type", VillagerType.PLAINS), texture(Hat.NONE),
            ApparitionLook.texture("type", VillagerType.SWAMP), texture(Hat.NONE),
            ApparitionLook.texture("type", VillagerType.SNOW), texture(Hat.FULL),
            ApparitionLook.texture("profession", VillagerProfession.FARMER), texture(Hat.PARTIAL),
            ApparitionLook.texture("profession", VillagerProfession.BUTCHER), texture(Hat.FULL),
            ApparitionLook.texture("profession", VillagerProfession.CLERIC), texture(Hat.NONE));
    private static final ResourceProvider RESOURCES = id -> Optional.ofNullable(VANILLA.get(id));

    @Test
    void texturesAreWhereTheGameKeepsThem() {
        assertEquals(Identifier.withDefaultNamespace("textures/entity/villager/type/swamp.png"),
                ApparitionLook.texture("type", VillagerType.SWAMP));
        assertEquals(Identifier.withDefaultNamespace("textures/entity/villager/profession/farmer.png"),
                ApparitionLook.texture("profession", VillagerProfession.FARMER));
    }

    @Test
    void theBiomesHatShowsAsTheGameDecides() {
        assertTrue(ApparitionLook.typeHatShows(Hat.FULL, Hat.NONE), "no profession hat: the biome's shows");
        assertTrue(ApparitionLook.typeHatShows(Hat.NONE, Hat.PARTIAL), "a partial hat over no hat");
        assertTrue(ApparitionLook.typeHatShows(Hat.PARTIAL, Hat.PARTIAL));
        assertFalse(ApparitionLook.typeHatShows(Hat.FULL, Hat.PARTIAL), "a full biome hat isn't worn under a partial one");
        assertFalse(ApparitionLook.typeHatShows(Hat.NONE, Hat.FULL), "a full profession hat covers it");
    }

    @Test
    void aLookTakesItsBiomesClothesAndItsProfessions() {
        var look = ApparitionLook.of(RESOURCES, VillagerType.SWAMP, VillagerProfession.CLERIC);
        assertEquals(ApparitionLook.texture("type", VillagerType.SWAMP), look.type());
        assertEquals(ApparitionLook.texture("profession", VillagerProfession.CLERIC), look.profession());
        assertTrue(look.typeHat());

        assertFalse(ApparitionLook.of(RESOURCES, VillagerType.SNOW, VillagerProfession.FARMER).typeHat(),
                "a snowy villager's hood comes off for a farmer's hat");
        assertFalse(ApparitionLook.of(RESOURCES, VillagerType.PLAINS, VillagerProfession.BUTCHER).typeHat());
        assertTrue(ApparitionLook.of(RESOURCES, VillagerType.PLAINS, VillagerProfession.FARMER).typeHat());
    }

    @Test
    void whatHasNoTextureIsLeftOut() {
        var none = ApparitionLook.of(RESOURCES, VillagerType.PLAINS, VillagerProfession.NONE);
        assertNull(none.profession(), "no profession, no profession layer");
        assertEquals(ApparitionLook.PLAIN, none);

        var missing = ApparitionLook.of(RESOURCES, VillagerType.TAIGA, VillagerProfession.LIBRARIAN);
        assertEquals(ApparitionLook.PLAINS, missing.type(), "a biome's clothes with no texture are the plains'");
        assertNull(missing.profession(), "a profession with no texture is drawn as none");
    }

    @Test
    void theProfessionComesFromTheSeed() {
        assertEquals(ApparitionLook.profession(1234L), ApparitionLook.profession(1234L), "every player sees the same one");
        var seen = new HashSet<>();
        for (long seed = 0; seed < 2000; seed++)
            seen.add(ApparitionLook.profession(seed));
        assertEquals(BuiltInRegistries.VILLAGER_PROFESSION.registryKeySet(), seen, "any of them comes up, none included");
    }
}
