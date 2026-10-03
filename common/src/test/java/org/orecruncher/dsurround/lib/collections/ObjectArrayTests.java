package org.orecruncher.dsurround.lib.collections;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectArrayTests {

    private static ObjectArray<String> of(String... values) {
        var array = new ObjectArray<String>();
        array.addAll(values);
        return array;
    }

    private static List<String> contents(ObjectArray<String> array) {
        var list = new ArrayList<String>();
        array.forEach(list::add);
        return list;
    }

    // ---- Adding and access -----------------------------------------------------------------------------------

    @Test
    void growsAsNeededFromAnyStartingSize() {
        for (int initial : new int[]{0, 1, 3, 4, 7}) {
            var array = new ObjectArray<Integer>(initial);
            for (int i = 0; i < 100; i++)
                array.add(i);

            assertEquals(100, array.size(), "initial " + initial);
            for (int i = 0; i < 100; i++)
                assertEquals(i, array.get(i), "initial " + initial);
        }
    }

    @Test
    void getOutOfRangeIsNull() {
        var array = of("a");

        assertNull(array.get(-1));
        assertNull(array.get(1));
        assertNull(new ObjectArray<String>().getFirst());
        assertNull(new ObjectArray<String>().getLast());
    }

    @Test
    void firstAndLast() {
        var array = of("a", "b", "c");

        assertEquals("a", array.getFirst());
        assertEquals("c", array.getLast());
    }

    @Test
    void copyConstructors() {
        var original = of("a", "b");
        var copy = new ObjectArray<>(original);
        original.add("c");

        assertEquals(List.of("a", "b"), contents(copy), "independent of the original");
        copy.add("d");
        assertEquals(List.of("a", "b", "d"), contents(copy));

        assertEquals(List.of("x", "y"), contents(new ObjectArray<>(new String[]{"x", "y"})));
        assertTrue(new ObjectArray<>(new ObjectArray<String>(0)).isEmpty());
    }

    @Test
    void arrayConstructorAcceptsOtherSubtypesLater() {
        // Regression: the copy kept the source array's runtime type, so adding another subtype threw
        // ArrayStoreException
        ObjectArray<CharSequence> array = new ObjectArray<>(new String[]{"a"});

        array.add(new StringBuilder("b"));

        assertEquals(2, array.size());
    }

    // ---- Removing --------------------------------------------------------------------------------------------

    @Test
    void removeFillsTheGapWithTheLastElement() {
        var array = of("a", "b", "c", "d");

        assertTrue(array.remove("b"));

        assertEquals(List.of("a", "d", "c"), contents(array));
        assertFalse(array.remove("zz"));
    }

    @Test
    void removeLastKeepsTheOrder() {
        var array = of("a", "b", "c");

        assertEquals("c", array.removeLast());

        assertEquals(List.of("a", "b"), contents(array));
        assertEquals("b", array.removeLast());
        assertEquals("a", array.removeLast());
        assertNull(array.removeLast(), "empty");
    }

    @Test
    void removeIfHandlesEveryArrangement() {
        var values = List.of("a1", "b1", "a2", "a3", "b2", "a4");

        var array = of(values.toArray(String[]::new));
        assertTrue(array.removeIf(s -> s.startsWith("a")));
        assertEquals(Set.of("b1", "b2"), new HashSet<>(contents(array)), "adjacent and first/last matches");

        array = of(values.toArray(String[]::new));
        assertTrue(array.removeIf(s -> true));
        assertTrue(array.isEmpty(), "all");

        array = of(values.toArray(String[]::new));
        assertFalse(array.removeIf(s -> false));
        assertEquals(values, contents(array), "none: order untouched");
    }

    @Test
    void removeIfMatchesASlowerReference() {
        // Many random arrangements against java.util's implementation (ignoring order)
        var random = new Random(99);
        for (int round = 0; round < 500; round++) {
            var values = new ArrayList<Integer>();
            int n = random.nextInt(20);
            for (int i = 0; i < n; i++)
                values.add(random.nextInt(10));
            int divisor = 1 + random.nextInt(4);

            var array = new ObjectArray<Integer>();
            array.addAll(values);
            array.removeIf(v -> v % divisor == 0);
            var expected = new ArrayList<>(values);
            expected.removeIf(v -> v % divisor == 0);

            var actual = new ArrayList<Integer>();
            array.forEach(actual::add);
            Collections.sort(actual);
            Collections.sort(expected);
            assertEquals(expected, actual, "values " + values + " divisor " + divisor);
        }
    }

    @Test
    void removeAllAndRetainAll() {
        var array = of("a", "b", "c", "d");
        array.removeAll(List.of("b", "d"));
        assertEquals(Set.of("a", "c"), new HashSet<>(contents(array)));

        array = of("a", "b", "c", "d");
        array.retainAll(List.of("b", "d"));
        assertEquals(Set.of("b", "d"), new HashSet<>(contents(array)));
    }

    @Test
    void clearEmptiesAndCanBeReused() {
        var array = of("a", "b");
        array.clear();

        assertTrue(array.isEmpty());
        array.add("c");
        assertEquals(List.of("c"), contents(array));
    }

    // ---- Nulls -----------------------------------------------------------------------------------------------

    @Test
    void nullsCanBeFoundAndRemoved() {
        // Regression: contains/remove called o.equals(...), so a null argument threw
        var array = of("a", null, "b");

        assertTrue(array.contains(null));
        assertFalse(of("a").contains(null));
        assertTrue(array.remove(null));
        assertFalse(array.contains(null));
        assertTrue(array.containsAll(List.of("a", "b")));
    }

    // ---- Iteration -------------------------------------------------------------------------------------------

    @Test
    void iteratorVisitsEachElementThenStops() {
        // Regression: next() past the end returned null or threw ArrayIndexOutOfBoundsException
        var iterator = of("a", "b").iterator();

        assertTrue(iterator.hasNext());
        assertEquals("a", iterator.next());
        assertEquals("b", iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void iteratorOfAnEmptyArray() {
        var iterator = new ObjectArray<String>().iterator();

        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void forEachLoopAndStream() {
        var array = of("a", "b", "c");
        var seen = new ArrayList<String>();
        for (var s : array)
            seen.add(s);

        assertEquals(List.of("a", "b", "c"), seen);
        assertEquals("abc", String.join("", array.stream().toList()));
    }

    // ---- Arrays, sorting, trimming ---------------------------------------------------------------------------

    @Test
    void toArrayCopiesOnlyTheElements() {
        var array = new ObjectArray<String>(10);
        array.add("a");
        array.add("b");

        assertArrayEquals(new Object[]{"a", "b"}, array.toArray());
        assertArrayEquals(new Object[0], new ObjectArray<String>().toArray());
    }

    @Test
    void typedToArray() {
        var array = of("a", "b");

        assertArrayEquals(new String[]{"a", "b"}, array.toArray(new String[0]), "too small: a new array");

        var big = new String[]{"x", "x", "x", "x"};
        assertSame(big, array.toArray(big), "large enough: filled in place");
        assertArrayEquals(new String[]{"a", "b", null, "x"}, big, "null after the last element");
    }

    @Test
    void sortCoversOnlyTheElements() {
        // Spare capacity holds nulls; sorting must not include them
        var array = new ObjectArray<String>(16);
        array.addAll(new String[]{"c", "a", "b"});

        array.sort(Comparator.naturalOrder());

        assertEquals(List.of("a", "b", "c"), contents(array));
    }

    @Test
    void trimKeepsTheElements() {
        var array = new ObjectArray<String>(16);
        array.addAll(new String[]{"a", "b"});
        array.trim();

        assertEquals(List.of("a", "b"), contents(array));
        array.add("c");
        assertEquals(List.of("a", "b", "c"), contents(array));

        var empty = new ObjectArray<String>(8);
        empty.trim();
        empty.add("x");
        assertEquals(List.of("x"), contents(empty));
    }

    @Test
    void toStringShowsTheContents() {
        assertEquals("[a, b]", of("a", "b").toString());
        assertEquals("[]", new ObjectArray<String>().toString());
    }

    @Test
    void equalityIsIdentity() {
        // Deliberate: records holding an ObjectArray rely on it (see SoundMapping)
        assertNotEquals(of("a"), of("a"));
        var array = of("a");
        assertEquals(array, array);
    }
}
