// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.runtime.weather.Wind;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the drops packed for building their quads ({@link PackedDrops}): each record holds what the field's arrays
 * say of its drop, rain and snow ahead of the dust, and the packing follows the drops as they change.
 */
public class PackedDropsTests {

    @Test
    void lightAndColorsArePackedAsWholeNumbers() {
        // As the int's bits, sky light below 8 or a color with little red was a denormal float, which a GPU may read
        // as 0: as whole numbers, nothing but 0 itself is under the smallest normal float
        for (int value : new int[]{0xF0, 0x000010, 0x102030, 0xF000F0, 0xFFFFFF}) {
            float packed = PackedDrops.whole(value);
            assertEquals(value, (int) packed);
            assertTrue(packed >= Float.MIN_NORMAL, Integer.toHexString(value));
        }
        assertEquals(0x123456, (int) PackedDrops.whole(0xFF123456), "only the color, not its alpha");
    }

    private static final double CAMERA_Y = 70.5;

    // Rain to the west, snow in the middle, dust to the east
    private record Columns() implements IColumnSource {
        public int groundHeight(int x, int z) {
            return 64;
        }

        public PrecipitationKind precipitation(int x, int y, int z) {
            return x < -4 ? PrecipitationKind.RAIN : x < 4 ? PrecipitationKind.SNOW : PrecipitationKind.DUST;
        }

        public int light(int x, int y, int z) {
            return 0xF000F0;
        }

        public int dustColor(int x, int y, int z) {
            return x >= 4 ? 0xC08040 : NOT_A_DUST_SOURCE;
        }

        public int fallbackDustColor(int x, int y, int z) {
            return 0xC08040;
        }
    }

    private static PrecipitationField field(double cameraX, double cameraZ) {
        var field = new PrecipitationField(4000);
        var random = Randomizer.create(17);
        for (int t = 0; t < 40; t++)
            field.tick(new Columns(), cameraX, CAMERA_Y, cameraZ, 1F, 1F, new Wind(), random);
        return field;
    }

    private static void checkRecords(PrecipitationField field, double cameraX, double cameraZ) {
        var d = field.drops();
        var packed = field.packed(cameraX + 0.3, CAMERA_Y, cameraZ - 0.7);
        assertEquals(d.count - d.dustCount, packed.solid);
        assertEquals(d.dustCount, packed.dust);

        // The drops' order, rain and snow first, then the dust
        var order = new ArrayList<Integer>();
        for (int i = 0; i < d.count; i++)
            if (d.kind[i] != PrecipitationField.DUST)
                order.add(i);
        for (int i = 0; i < d.count; i++)
            if (d.kind[i] == PrecipitationField.DUST)
                order.add(i);

        float partialTick = 0.25F;
        var r = packed.data;
        for (int n = 0; n < order.size(); n++) {
            int i = order.get(n), at = n * PackedDrops.STRIDE;
            byte kind = d.kind[i];
            assertEquals(kind, (byte) r[at + PackedDrops.KIND]);
            double x = packed.anchorX + r[at + PackedDrops.PREV_X] + partialTick * r[at + PackedDrops.MOVE_X];
            double y = packed.anchorY + r[at + PackedDrops.PREV_Y] + partialTick * r[at + PackedDrops.MOVE_Y];
            double z = packed.anchorZ + r[at + PackedDrops.PREV_Z] + partialTick * r[at + PackedDrops.MOVE_Z];
            assertEquals(Mth.lerp(partialTick, d.prevX[i], d.x[i]), x, 1e-4);
            assertEquals(Mth.lerp(partialTick, d.prevY[i], d.y[i]), y, 1e-4);
            assertEquals(Mth.lerp(partialTick, d.prevZ[i], d.z[i]), z, 1e-4);
            assertEquals(d.light[i], (int) r[at + PackedDrops.LIGHT]);
            assertEquals(d.thinning[i], r[at + PackedDrops.THINNING]);
            assertEquals(PrecipitationField.fade(kind, d.age[i], d.lifetime[i], d.covered[i], partialTick),
                    PrecipitationField.fade(kind, r[at + PackedDrops.AGE], r[at + PackedDrops.LIFETIME], r[at + PackedDrops.COVERED], partialTick));
            assertEquals(PrecipitationField.pick(d, i), r[at + PackedDrops.PICK]);
            switch (kind) {
                case PrecipitationField.RAIN -> {
                    assertEquals(PrecipitationField.rainSize(d, i), r[at + PackedDrops.SIZE]);
                    float a = r[at + PackedDrops.A], b = r[at + PackedDrops.B], c = r[at + PackedDrops.C];
                    assertEquals(1F, a * a + b * b + c * c, 1e-3F, "the way it falls, a unit vector");
                    assertTrue(b < 0F, "falling");
                }
                case PrecipitationField.SNOW -> {
                    assertEquals(PrecipitationField.snowSize(d, i), r[at + PackedDrops.SIZE]);
                    assertEquals(d.spinCos[i], r[at + PackedDrops.A]);
                    assertEquals(d.spinSin[i], r[at + PackedDrops.B]);
                }
                default -> {
                    assertEquals(PrecipitationField.dustSize(d, i), r[at + PackedDrops.SIZE]);
                    assertEquals(d.color[i], (int) r[at + PackedDrops.C]);
                }
            }
        }
    }

    @Test
    void eachRecordHoldsItsDrop() {
        var field = field(0.5, 0.5);
        var d = field.drops();
        assertTrue(d.dustCount > 0 && d.dustCount < d.count, "rain or snow, and dust");
        checkRecords(field, 0.5, 0.5);
    }

    @Test
    void farFromTheWorldsMiddleTheyAreAsExact() {
        double far = 1_500_000.5;
        checkRecords(field(far, -far), far, -far);
    }

    @Test
    void theyArePackedAgainOnceTheDropsMove() {
        var field = field(0.5, 0.5);
        var before = field.packed(0.5, CAMERA_Y, 0.5).data[PackedDrops.PREV_Y];
        field.tick(new Columns(), 0.5, CAMERA_Y, 0.5, 1F, 1F, new Wind(), Randomizer.create(3));
        checkRecords(field, 0.5, 0.5);
        assertNotEquals(before, field.packed(0.5, CAMERA_Y, 0.5).data[PackedDrops.PREV_Y]);

        field.clear();
        var packed = field.packed(0.5, CAMERA_Y, 0.5);
        assertEquals(0, packed.solid + packed.dust, "none after clearing");
    }
}
