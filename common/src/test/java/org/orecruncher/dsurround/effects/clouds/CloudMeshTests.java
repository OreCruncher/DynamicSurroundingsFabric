package org.orecruncher.dsurround.effects.clouds;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.runtime.weather.CloudCover;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for turning the cloud columns into quads.
 */
public class CloudMeshTests {

    private record Vertex(float x, float y, float z, float shade, float alpha, float start, float direction) {
    }

    /**
     * The settled part's vertices; for a field with nothing changing
     */
    private static List<Vertex> build(CloudField field) {
        var changing = new ArrayList<Vertex>();
        var settled = build(field, changing);
        assertTrue(changing.isEmpty(), "nothing changing");
        return settled;
    }

    private static List<Vertex> build(CloudField field, List<Vertex> changing) {
        var settled = new ArrayList<Vertex>();
        int quads = CloudMesh.build(field,
                (x, y, z, shade, alpha, start, direction) -> settled.add(new Vertex(x, y, z, shade, alpha, start, direction)),
                (x, y, z, shade, alpha, start, direction) -> changing.add(new Vertex(x, y, z, shade, alpha, start, direction)));
        assertEquals(quads * 4, settled.size() + changing.size());
        assertTrue(settled.stream().allMatch(v -> v.direction == 0F && v.start == 0F), "the settled part doesn't fade");
        return settled;
    }

    private static Vector3f at(Vertex v) {
        return new Vector3f(v.x, v.y, v.z);
    }

    @Test
    void aLoneColumnIsABox() {
        var field = new CloudField();
        // Everything covered, then only the middle cell kept: a radius of 0 is one cell
        field.update(new CloudCover(1F, 0F, 8F, 8F, 0F, 0F), 0, 0, 0, 0, 1F, 0F);
        assertEquals(1, field.count(CloudField.Layer.LOW));
        var vertices = build(field);
        assertEquals(6 * 4, vertices.size(), "a top, a base and four sides");
    }

    @Test
    void everyQuadFacesOutOfItsCloud() {
        var field = new CloudField();
        field.update(CloudCover.of(1F, 1F, 8000, 1F, 1F), 3, -2, 6, 2, 0.6F, 0.8F);
        var vertices = build(field);
        assertFalse(vertices.isEmpty());
        for (int q = 0; q < vertices.size(); q += 4) {
            var v0 = at(vertices.get(q));
            var v1 = at(vertices.get(q + 1));
            var v2 = at(vertices.get(q + 2));
            var v3 = at(vertices.get(q + 3));
            // Counter-clockwise from outside: the winding's normal points away from the middle of the cell's column
            var normal = new Vector3f(v1).sub(v0).cross(new Vector3f(v2).sub(v0));
            assertTrue(normal.lengthSquared() > 0F, "a flat quad at " + q);
            normal.normalize();
            var middle = new Vector3f(v0).add(v1).add(v2).add(v3).div(4F);
            int i = (int) Math.floor((middle.x - normal.x * 0.01F) / CloudField.CELL);
            int j = (int) Math.floor((middle.z - normal.z * 0.01F) / CloudField.CELL);
            // Just inside the face is cloud; just outside, along the normal, isn't (for sides) or is past the top/base
            assertTrue(field.has(CloudField.Layer.LOW, i, j) || field.has(CloudField.Layer.HIGH, i, j), "inside a cloud at " + q);
            if (normal.y > 0F)
                assertTrue(middle.y == field.top(CloudField.Layer.LOW, i, j) || middle.y == field.top(CloudField.Layer.HIGH, i, j), "a top");
            else if (normal.y < 0F)
                assertTrue(middle.y == field.base(CloudField.Layer.LOW, i, j) || middle.y == field.base(CloudField.Layer.HIGH, i, j), "a base");
            else {
                // A side: the middle of its edge is on the cell's edge, facing out of the cell
                float cx = (i + 0.5F) * CloudField.CELL;
                float cz = (j + 0.5F) * CloudField.CELL;
                float out = (middle.x - cx) * normal.x + (middle.z - cz) * normal.z;
                assertTrue(out > 0F, "a side facing in at " + q);
            }
        }
    }

    @Test
    void sidesBetweenColumnsOfTheSameHeightAreLeftOut() {
        var field = new CloudField();
        // A flat deck, everything covered, the same thickness: only the grid's edge has sides
        field.update(new CloudCover(1F, 0F, 8F, 8F, 0F, 0F), 0, 0, 3, 0, 1F, 0F);
        int size = field.size();
        assertEquals(size * size, field.count(CloudField.Layer.LOW));
        var vertices = build(field);
        int quads = vertices.size() / 4;
        assertEquals(2 * size * size + 4 * size, quads);
    }

    @Test
    void basesAreDarkerUnderThickerClouds() {
        var thin = new CloudField();
        thin.update(new CloudCover(1F, 0F, CloudCover.FLAT, CloudCover.FLAT, 0F, 0F), 0, 0, 0, 0, 1F, 0F);
        var thick = new CloudField();
        thick.update(new CloudCover(1F, 0F, 40F, 40F, 0F, 0F), 0, 0, 0, 0, 1F, 0F);
        float thinBase = baseShade(build(thin));
        float thickBase = baseShade(build(thick));
        assertEquals(CloudMesh.THIN_BASE, thinBase, 1e-5F);
        assertEquals(CloudMesh.THICK_BASE, thickBase, 1e-5F);
        // Tops are lit in full
        assertTrue(build(thick).stream().filter(v -> v.y == thick.top(CloudField.Layer.LOW, 0, 0)).allMatch(v -> v.shade >= CloudMesh.Z_SIDE));
    }

    private static float baseShade(List<Vertex> vertices) {
        // The second quad is the base
        return vertices.get(4).shade;
    }

    @Test
    void aCloudThatGoesFadesOutWhereItWas() {
        var field = new CloudField();
        field.update(new CloudCover(1F, 0F, 8F, 8F, 0F, 0F), 0, 0, 0, 0, 1F, 0F, 100, 0, 1);
        // Gone, five ticks later: nothing settled, its box fading out
        field.update(CloudCover.CLEAR, 0, 0, 0, 0, 1F, 0F, 105, 0, 1);
        assertTrue(field.isFading(0, 0));
        assertFalse(field.isEmpty(), "still drawn while it fades");
        var changing = new ArrayList<Vertex>();
        var settled = build(field, changing);
        assertTrue(settled.isEmpty());
        assertEquals(6 * 4, changing.size(), "the whole box");
        assertTrue(changing.stream().allMatch(v -> v.direction == -1F && v.start == 0F), "fading out, from this tick");
        assertEquals(8F, changing.stream().map(v -> v.y).max(Float::compare).orElseThrow(), 1e-6F);
    }

    @Test
    void aStepAddedFadesInAboveTheSettledPart() {
        var field = new CloudField();
        field.update(new CloudCover(1F, 0F, 8F, 8F, 0F, 0F), 0, 0, 0, 0, 1F, 0F, 100, 0, 1);
        field.update(new CloudCover(1F, 0F, 12F, 12F, 0F, 0F), 0, 0, 0, 0, 1F, 0F, 101, 0, 1);
        var changing = new ArrayList<Vertex>();
        var settled = build(field, changing);
        // The 8 blocks it had stay settled, as a whole box; the new step, 8 to 12, fades in, without a base against them
        assertEquals(6 * 4, settled.size());
        assertEquals(8F, settled.stream().map(v -> v.y).max(Float::compare).orElseThrow(), 1e-6F);
        assertEquals(5 * 4, changing.size(), "a top and four sides");
        assertTrue(changing.stream().allMatch(v -> v.direction == 1F && v.y >= 8F && v.y <= 12F));
    }

    @Test
    void aFadeIsOverAfterItsTicksAndTheCloudSettles() {
        var field = new CloudField();
        var thin = new CloudCover(1F, 0F, 8F, 8F, 0F, 0F);
        var thick = new CloudCover(1F, 0F, 12F, 12F, 0F, 0F);
        field.update(thin, 0, 0, 0, 0, 1F, 0F, 100, 0, 1);
        field.update(thick, 0, 0, 0, 0, 1F, 0F, 101, 0, 1);
        int version = field.version();
        // Ticking on: still fading, and not worked out again meanwhile, even if the clouds would change
        for (long t = 102; t < 101 + CloudField.FADE_TICKS; t++) {
            field.update(thin, 0, 0, 0, 0, 1F, 0F, t, 0, 1);
            assertTrue(field.isFading(0, 0), "at " + t);
            assertEquals(12, field.top(CloudField.Layer.LOW, 0, 0));
        }
        assertEquals(version, field.version(), "nothing to build again while it fades");
        // Over: built again, all settled
        assertTrue(field.update(thick, 0, 0, 0, 0, 1F, 0F, 101 + CloudField.FADE_TICKS, 0, 1));
        assertFalse(field.isFading(0, 0));
        assertEquals(6 * 4, build(field).size());
    }
}
