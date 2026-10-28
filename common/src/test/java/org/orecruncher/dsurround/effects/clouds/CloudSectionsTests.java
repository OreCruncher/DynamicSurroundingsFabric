package org.orecruncher.dsurround.effects.clouds;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.runtime.weather.CloudCover;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for keeping the clouds' quads in sections, built again only where cells changed: what they hold must always be
 * what building every quad would give.
 */
public class CloudSectionsTests {

    private static final int RADIUS = 20;
    private static final long AFTERNOON = 8000;

    /**
     * A vertex, without its change's start (which counts from a different tick in each)
     */
    private record Vertex(float x, float y, float z, float direction, int shade, int alpha) implements Comparable<Vertex> {
        @Override
        public int compareTo(Vertex o) {
            int c = Float.compare(this.x, o.x);
            if (c == 0) c = Float.compare(this.y, o.y);
            if (c == 0) c = Float.compare(this.z, o.z);
            if (c == 0) c = Float.compare(this.direction, o.direction);
            if (c == 0) c = Integer.compare(this.shade, o.shade);
            if (c == 0) c = Integer.compare(this.alpha, o.alpha);
            return c;
        }
    }

    private static List<Vertex> read(ByteBuffer buffer, int count) {
        var vertices = new ArrayList<Vertex>();
        var b = buffer.duplicate().order(buffer.order());
        for (int v = 0; v < count; v++) {
            float x = b.getFloat(), y = b.getFloat(), z = b.getFloat();
            b.getFloat();
            float direction = b.getFloat();
            int shade = b.get() & 0xFF;
            b.get();
            b.get();
            int alpha = b.get() & 0xFF;
            vertices.add(new Vertex(x, y, z, direction, shade, alpha));
        }
        vertices.sort(null);
        return vertices;
    }

    /**
     * Every quad of the field, built at once, from the sections' anchor
     */
    private static List<Vertex> everything(CloudField field, CloudSections sections, boolean settledPart) {
        var vertices = new ArrayList<Vertex>();
        CloudMesh.Sink sink = (x, y, z, shade, alpha, start, direction) ->
                vertices.add(new Vertex(x, y, z, direction, (int) (shade * 255F) & 0xFF, (int) (alpha * 255F) & 0xFF));
        CloudMesh.Sink none = (x, y, z, shade, alpha, start, direction) -> {
        };
        CloudMesh.build(field, 0, 0, field.size(), field.size(), field.originX() - sections.anchorX(), field.originZ() - sections.anchorZ(),
                settledPart ? sink : none, settledPart ? none : sink);
        vertices.sort(null);
        return vertices;
    }

    private static void assertMatches(CloudField field, CloudSections sections) {
        assertEquals(everything(field, sections, true), read(sections.settled(), sections.settledVertices()), "settled");
        assertEquals(everything(field, sections, false), read(sections.changing(), sections.changingVertices()), "changing");
    }

    @Test
    void holdsEveryQuad() {
        var field = new CloudField();
        field.update(CloudCover.of(0.3F, 0F, AFTERNOON, 0.7F, 0.6F), 0, 0, RADIUS, 0, 1F, 0F, 0, 0, 1);
        var sections = new CloudSections();
        assertTrue(sections.update(field));
        assertTrue(sections.settledVertices() > 0);
        assertMatches(field, sections);
        assertFalse(sections.update(field), "nothing changed");
    }

    @Test
    void buildsAgainOnlyWhereCellsChanged() {
        var cover = CloudCover.of(0.3F, 0F, AFTERNOON, 0.7F, 0.6F);
        var field = new CloudField();
        field.update(cover, 0, 0, RADIUS, 0, 1F, 0F, 0, 0, 1);
        var sections = new CloudSections();
        sections.update(field);
        int all = sections.sections();

        // The clouds changing at twice the game's rate (CloudHandler.CHANGE_PER_TICK), a twentieth of the cells on
        // each tick's turn: the few sections where cells changed are built, not all of them
        long tick = 100;
        int updates = 0, built = 0;
        for (int t = 0; t < 400; t++, tick++) {
            field.update(cover, 0, 0, RADIUS, tick * (2D / 3000D), 1F, 0F, tick, (int) (tick % 20), 20);
            if (sections.update(field)) {
                updates++;
                built += sections.built();
                assertMatches(field, sections);
            }
        }
        assertTrue(updates > 0, "some changed");
        assertTrue(built < updates * all / 4, built + " built over " + updates + " updates, of " + all);
        // Fades ending, a fade's length on
        for (int t = 0; t < CloudField.FADE_TICKS + 1; t++, tick++) {
            field.update(cover, 0, 0, RADIUS, 0.2, 1F, 0F, tick, (int) (tick % 20), 20);
            sections.update(field);
            assertMatches(field, sections);
        }
    }

    @Test
    void stayRightAsTheGridMoves() {
        var cover = CloudCover.of(0.3F, 0F, AFTERNOON, 0.7F, 0.6F);
        var field = new CloudField();
        field.update(cover, 0, 0, RADIUS, 0, 1F, 0F, 0, 0, 1);
        var sections = new CloudSections();
        sections.update(field);
        long tick = 1;
        // East a cell at a time, past several sections; then a jump north-west
        for (int cx = 1; cx <= 20; cx++, tick++) {
            field.update(cover, cx, 0, RADIUS, 0, 1F, 0F, tick, (int) (tick % 20), 20);
            sections.update(field);
            assertMatches(field, sections);
        }
        field.update(cover, 3, -11, RADIUS, 0, 1F, 0F, tick, (int) (tick % 20), 20);
        sections.update(field);
        assertMatches(field, sections);
    }

    @Test
    void startsAgainWhenTheFieldDoes() {
        var field = new CloudField();
        field.update(new CloudCover(1F, 0F, 8F, 8F, 0F, 0F), 0, 0, RADIUS, 0, 1F, 0F, 0, 0, 1);
        var sections = new CloudSections();
        sections.update(field);
        field.clear();
        assertTrue(sections.update(field));
        assertEquals(0, sections.settledVertices());
        assertEquals(0, sections.changingVertices());
        // A new grid size
        field.update(new CloudCover(1F, 0F, 8F, 8F, 0F, 0F), 0, 0, RADIUS + 3, 0, 1F, 0F, 1, 0, 1);
        sections.update(field);
        assertMatches(field, sections);
    }
}
