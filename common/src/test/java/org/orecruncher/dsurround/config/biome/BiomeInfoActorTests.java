// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.biome;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.ActorKind;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The world actors a biome gives, from the {@code actors} lists of the rules that match it.
 */
public class BiomeInfoActorTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BiomeConfigRule rule(String actors) {
        var json = "{\"biomeSelector\": \"true\", \"actors\": [" + actors + "]}";
        return BiomeConfigRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void aBiomeGivesTheActorsItsRulesName() {
        var info = BiomeInfoMusicTests.info(rule("{\"actor\": \"dsurround:dust_devil\", \"conditions\": \"diurnal.isDay()\", \"chance\": 0.2}"));
        var entry = info.getActor(ActorKind.DUST_DEVIL);
        assertNotNull(entry);
        assertEquals(0.2F, entry.chance(), 1e-6F);
        assertTrue(entry.matches());
        assertTrue(info.toString().contains("dsurround:dust_devil"), info.toString());
    }

    @Test
    void theChanceHasADefault() {
        var entry = BiomeInfoMusicTests.info(rule("{\"actor\": \"dsurround:dust_devil\"}")).getActor(ActorKind.DUST_DEVIL);
        assertNotNull(entry);
        assertTrue(entry.chance() > 0F && entry.chance() < 1F);
    }

    @Test
    void aLaterRuleReplacesAnEarlierOnesActor() {
        var info = BiomeInfoMusicTests.info(
                rule("{\"actor\": \"dsurround:dust_devil\", \"chance\": 0.2}"),
                rule("{\"actor\": \"dsurround:dust_devil\", \"chance\": 0.5}"));
        assertEquals(1, info.getActors().size());
        assertEquals(0.5F, info.getActor(ActorKind.DUST_DEVIL).chance(), 1e-6F);
    }

    @Test
    void aChanceOfNothingTakesTheActorAway() {
        var info = BiomeInfoMusicTests.info(
                rule("{\"actor\": \"dsurround:dust_devil\", \"chance\": 0.2}"),
                rule("{\"actor\": \"dsurround:dust_devil\", \"chance\": 0}"));
        assertNull(info.getActor(ActorKind.DUST_DEVIL));
        assertTrue(info.getActors().isEmpty());
    }

    @Test
    void anUnknownActorIsLeftOutAndTheRestKept() {
        var info = BiomeInfoMusicTests.info(rule("{\"actor\": \"test:tumbleweed\"}, {\"actor\": \"dsurround:dust_devil\"}"));
        assertEquals(1, info.getActors().size());
        assertNotNull(info.getActor(ActorKind.DUST_DEVIL));
    }

    @Test
    void aChanceOutsideZeroToOneIsRefused() {
        var json = "{\"biomeSelector\": \"true\", \"actors\": [{\"actor\": \"dsurround:dust_devil\", \"chance\": 1.5}]}";
        assertTrue(BiomeConfigRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
    }

    @Test
    void aBiomeWithNoActorsGivesNone() {
        var info = BiomeInfoMusicTests.info();
        assertTrue(info.getActors().isEmpty());
        assertNull(info.getActor(ActorKind.DUST_DEVIL));
    }
}
