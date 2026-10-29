package org.orecruncher.dsurround.effects.entity;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for where animals breathe from, how big their breath is, and which animals breathe.
 */
public class AnimalBreathTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final double EPSILON = 1e-6;
    private static final Muzzle.Profile COW = Muzzle.PROFILES.get(EntityType.COW);

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON, "x of " + actual);
        assertEquals(expected.y, actual.y, EPSILON, "y of " + actual);
        assertEquals(expected.z, actual.z, EPSILON, "z of " + actual);
    }

    // ---- Where the mouth is --------------------------------------------------------------------------------------

    @Test
    void aCowsMouthIsOutInFront() {
        // Yaw 0 faces south, +z. The head's pivot is 8 pixels in front and 20 up; the mouth 7 further, 2.5 below it
        var mouth = Muzzle.position(10, 64, 20, 0F, 0F, 0F, COW, 1D);
        assertNear(new Vec3(10, 64 + 17.5 / 16D, 20 + 15 / 16D), mouth);
    }

    @Test
    void theMouthFollowsTheBodyThenTheHead() {
        // Body facing south, head turned to the east (yaw -90 faces +x): the neck stays in front of the body
        var mouth = Muzzle.position(0, 0, 0, 0F, -90F, 0F, COW, 1D);
        assertNear(new Vec3(7 / 16D, 17.5 / 16D, 8 / 16D), mouth);
    }

    @Test
    void lookingDownTipsTheMouthAboutTheNeck() {
        var level = Muzzle.position(0, 0, 0, 0F, 0F, 0F, COW, 1D);
        var down = Muzzle.position(0, 0, 0, 0F, 0F, 45F, COW, 1D);
        var up = Muzzle.position(0, 0, 0, 0F, 0F, -45F, COW, 1D);

        assertTrue(down.y < level.y, "lower looking down");
        assertTrue(up.y > level.y, "higher looking up");
        // Turned about the pivot, so as far from it as ever
        var pivot = new Vec3(0, 20 / 16D, 8 / 16D);
        assertEquals(level.distanceTo(pivot), down.distanceTo(pivot), EPSILON);
        assertEquals(level.distanceTo(pivot), up.distanceTo(pivot), EPSILON);
    }

    @Test
    void aHoglinsHeadIgnoresItsPitch() {
        var hoglin = Muzzle.PROFILES.get(EntityType.HOGLIN);
        assertNear(Muzzle.position(0, 0, 0, 30F, 30F, 0F, hoglin, 1D),
                Muzzle.position(0, 0, 0, 30F, 30F, 40F, hoglin, 1D));
    }

    @Test
    void aCalfsMouthIsHalfAsFarOut() {
        var grown = Muzzle.position(0, 0, 0, 0F, 0F, 20F, COW, 1D);
        var calf = Muzzle.position(0, 0, 0, 0F, 0F, 20F, COW, 0.5D);
        assertNear(grown.scale(0.5D), calf);
    }

    @Test
    void anUnmeasuredMobBreathesFromTheFrontOfItsBox() {
        var guess = Muzzle.guess(1.0D, 1.2D);
        var mouth = Muzzle.position(0, 0, 0, 0F, 0F, 0F, guess, 1D);
        assertTrue(mouth.z > 0.5D, "past the front of the box: " + mouth);
        assertTrue(mouth.y < 1.2D, "below the eyes: " + mouth);
    }

    @Test
    void mouthsAreWhereTheMobsAre() {
        // Each grown mob's mouth, level and facing south, should be in front of its middle and no higher than a
        // little over its box: a sign slip in a model's numbers puts it behind, or under the ground
        for (var entry : Muzzle.PROFILES.entrySet()) {
            var type = entry.getKey();
            var mouth = Muzzle.position(0, 0, 0, 0F, 0F, 0F, entry.getValue(), 1D);
            var name = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            assertTrue(mouth.z > type.getWidth() * 0.25D, name + " forward: " + mouth);
            assertTrue(mouth.y > 0D && mouth.y < type.getHeight() * 1.25D, name + " height: " + mouth);
        }
    }

    @Test
    void aWolfBreathesOverTheSnowItStandsIn() {
        // Its mouth is half a block up, in the block a snow layer (2 pixels) fills the bottom of
        var wolf = Muzzle.position(0, 0, 0, 0F, 0F, 0F, Muzzle.PROFILES.get(EntityType.WOLF), 1D);
        assertTrue(BreathEffect.breathesFreely(2 / 16D, wolf.y), "mouth at " + wolf.y);
        // Nor does grass, which has no collision shape, stop it
        assertTrue(BreathEffect.breathesFreely(Double.NEGATIVE_INFINITY, wolf.y));
        // In air, anywhere in the block
        assertTrue(BreathEffect.breathesFreely(0D, 0D));
        // But not in a deep drift
        assertFalse(BreathEffect.breathesFreely(14 / 16D, wolf.y));
    }

    // ---- How big the breath is -----------------------------------------------------------------------------------

    @Test
    void aPlayerSizedMobBreathesAPlayersPuff() {
        assertEquals(0.25F, AnimalBreathEffect.puffSize(0.6D, 1.8D), 1e-6F);
    }

    @Test
    void biggerMobsBreatheBiggerPuffsWithinLimits() {
        float cat = AnimalBreathEffect.puffSize(0.48D, 0.56D);
        float cow = AnimalBreathEffect.puffSize(0.9D, 1.4D);
        float ravager = AnimalBreathEffect.puffSize(1.95D, 2.2D);
        assertTrue(cat < cow && cow < ravager, cat + " " + cow + " " + ravager);
        assertEquals(0.125F, AnimalBreathEffect.puffSize(0.1D, 0.1D), 1e-6F);
        assertEquals(0.5F, AnimalBreathEffect.puffSize(5D, 5D), 1e-6F);
    }

    // ---- Who breathes --------------------------------------------------------------------------------------------

    private static List<String> tagValues(String name) throws IOException {
        var path = "/assets/dsurround/dsconfigs/tags/entity_type/effects/" + name + ".json";
        try (InputStream in = AnimalBreathTests.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            var values = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("values");
            var result = new ArrayList<String>();
            values.forEach(v -> result.add(v.getAsString()));
            return result;
        }
    }

    @Test
    void everyAnimalThatBreathesIsMeasured() throws IOException {
        for (var id : tagValues("animal_breath")) {
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id));
            assertTrue(type.isPresent(), id + " is an entity");
            assertTrue(Muzzle.PROFILES.containsKey(type.get()), id + " has a muzzle profile");
        }
    }

    @Test
    void fishSquidChickensAndTheUndeadDontBreathe() throws IOException {
        var animals = tagValues("animal_breath");
        for (var id : List.of("minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish",
                "minecraft:squid", "minecraft:glow_squid", "minecraft:chicken", "minecraft:zombie_horse",
                "minecraft:skeleton_horse", "minecraft:zoglin"))
            assertFalse(animals.contains(id), id);
    }

    @Test
    void noMobBreathesBothWays() throws IOException {
        var people = tagValues("frost_breath");
        for (var id : tagValues("animal_breath"))
            assertFalse(people.contains(id), id);
    }
}
