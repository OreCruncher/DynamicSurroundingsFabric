// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.precipitation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for a set of drops' copying: a tick's move copies the set whole, then drop by drop to close gaps, so both must
 * carry every array a drop has.
 */
public class DropsTests {

    private static final int CAPACITY = 8;
    // Where a drop was: the whole copy sets it from where it is now
    private static final Set<String> PREVIOUS = Set.of("prevX", "prevY", "prevZ");

    private static List<Field> arrays() {
        return Arrays.stream(PrecipitationField.Drops.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && f.getType().isArray())
                .peek(f -> f.setAccessible(true))
                .toList();
    }

    // A set whose every array holds values no other array has, drop by drop
    private static PrecipitationField.Drops filled(int count) throws Exception {
        var drops = new PrecipitationField.Drops(CAPACITY);
        var fields = arrays();
        for (int f = 0; f < fields.size(); f++) {
            var array = fields.get(f).get(drops);
            for (int i = 0; i < CAPACITY; i++) {
                int value = f * 16 + i + 1;
                switch (array) {
                    case double[] a -> a[i] = value;
                    case float[] a -> a[i] = value;
                    case int[] a -> a[i] = value;
                    case byte[] a -> a[i] = (byte) value;
                    default -> fail("A drop's array of a type the test doesn't fill: " + fields.get(f).getName());
                }
            }
        }
        drops.count = count;
        return drops;
    }

    private static double value(Object array, int i) {
        return ((Number) Array.get(array, i)).doubleValue();
    }

    @Test
    void copyingAllCarriesEveryArray() throws Exception {
        var from = filled(5);
        var to = new PrecipitationField.Drops(CAPACITY);
        to.copyAllOf(from);
        for (var field : arrays()) {
            String source = PREVIOUS.contains(field.getName()) ? field.getName().substring(4).toLowerCase() : field.getName();
            var expected = PrecipitationField.Drops.class.getDeclaredField(source);
            expected.setAccessible(true);
            for (int i = 0; i < 5; i++)
                assertEquals(value(expected.get(from), i), value(field.get(to), i), field.getName() + "[" + i + "]");
            // Past the drops there are, nothing is copied
            assertEquals(0D, value(field.get(to), 5), field.getName() + " past the drops");
        }
    }

    @Test
    void copyingADropCarriesEveryArray() throws Exception {
        var from = filled(5);
        var to = new PrecipitationField.Drops(CAPACITY);
        from.copyTo(3, to, 1);
        for (var field : arrays())
            assertEquals(value(field.get(from), 3), value(field.get(to), 1), field.getName());
    }
}
