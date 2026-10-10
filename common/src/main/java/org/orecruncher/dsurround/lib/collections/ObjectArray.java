package org.orecruncher.dsurround.lib.collections;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A lightweight growable array: a bag of elements with indexed access, iterated without allocation by
 * {@link #forEach}.
 * <p>
 * <b>Removing an element moves the last element into its place</b> ({@link #remove}, {@link #removeIf},
 * {@link #removeAll}, {@link #retainAll}), so removal is fast but doesn't keep the order. {@link #removeLast()}
 * keeps it. Elements added are kept in insertion order until something is removed.
 * <p>
 * Don't change the array while iterating it: removals move elements, so some would be skipped or visited twice.
 * Use {@link #removeIf} instead.
 * <p>
 * Equality is identity (as for Object): records holding an ObjectArray compare equal only if they hold the same
 * instance. Not thread-safe.
 */
public class ObjectArray<T> implements Collection<T> {

    private static final int DEFAULT_SIZE = 4;
    private static final Object[] EMPTY_ARRAY = new Object[0];

    private Object[] data;
    private int insertionIdx;

    public ObjectArray() {
        this(DEFAULT_SIZE);
    }

    public ObjectArray(final int size) {
        this.data = size == 0 ? EMPTY_ARRAY : new Object[size];
    }

    public ObjectArray(final ObjectArray<T> input) {
        this.data = Arrays.copyOf(input.data, input.size());
        this.insertionIdx = input.insertionIdx;
    }

    public ObjectArray(final T[] input) {
        this.data = Arrays.copyOf(input, input.length, Object[].class);
        this.insertionIdx = input.length;
    }

    private void grow() {
        this.data = Arrays.copyOf(this.data, Math.max(this.data.length * 2, DEFAULT_SIZE));
    }

    /**
     * Sorts the elements.
     */
    @SuppressWarnings("unchecked")
    public void sort(Comparator<? super T> c) {
        Arrays.sort((T[]) this.data, 0, this.insertionIdx, c);
    }

    @Override
    public int size() {
        return this.insertionIdx;
    }

    /**
     * The element at the index, or null if the index is out of range.
     */
    @SuppressWarnings("unchecked")
    public T get(final int idx) {
        if (idx >= 0 && idx < this.insertionIdx)
            return (T) this.data[idx];
        return null;
    }

    /**
     * The first element, or null if empty.
     */
    public T getFirst() {
        return this.get(0);
    }

    /**
     * The last element, or null if empty.
     */
    public T getLast() {
        return this.get(this.size() - 1);
    }

    /**
     * Removes and returns the last element, keeping the order of the rest. Null if empty.
     */
    @SuppressWarnings("unchecked")
    public T removeLast() {
        if (this.insertionIdx == 0)
            return null;
        final T last = (T) this.data[--this.insertionIdx];
        this.data[this.insertionIdx] = null;
        return last;
    }

    /**
     * Removes the element at the index by moving the last element into its place.
     */
    private void removeAt(final int idx) {
        final Object m = this.data[--this.insertionIdx];
        this.data[this.insertionIdx] = null;
        if (idx < this.insertionIdx)
            this.data[idx] = m;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean removeIf(@NotNull final Predicate<? super T> filter) {
        boolean result = false;
        // From the end: an element moved into a removed slot has already been tested
        for (int i = this.insertionIdx - 1; i >= 0; i--) {
            final T t = (T) this.data[i];
            if (filter.test(t)) {
                result = true;
                this.removeAt(i);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void forEach(final Consumer<? super T> consumer) {
        for (int i = 0; i < this.insertionIdx; i++)
            consumer.accept((T) this.data[i]);
    }

    @Override
    public boolean isEmpty() {
        return this.insertionIdx == 0;
    }

    private int find(final Object o) {
        for (int i = 0; i < this.insertionIdx; i++)
            if (Objects.equals(o, this.data[i]))
                return i;
        return -1;
    }

    @Override
    public boolean contains(final Object o) {
        return this.find(o) != -1;
    }

    @Override
    public Object @NotNull [] toArray() {
        return this.insertionIdx == 0 ? EMPTY_ARRAY : Arrays.copyOf(this.data, this.insertionIdx);
    }

    @SuppressWarnings({"unchecked", "TypeParameterHidesVisibleType"})
    @Override
    public <T> T @NotNull [] toArray(final T[] a) {
        // From ArrayList impl
        if (a.length < this.insertionIdx)
            // Make a new array of a's runtime type, but my contents:
            return (T[]) Arrays.copyOf(this.data, this.insertionIdx, a.getClass());
        System.arraycopy(this.data, 0, a, 0, this.insertionIdx);
        if (a.length > this.insertionIdx)
            a[this.insertionIdx] = null;
        return a;
    }

    @Override
    public boolean add(final T e) {
        if (this.data.length == this.insertionIdx)
            this.grow();
        this.data[this.insertionIdx++] = e;
        return true;
    }

    /**
     * Removes the first element equal to {@code o}, moving the last element into its place.
     */
    @Override
    public boolean remove(final Object o) {
        final int idx = this.find(o);
        if (idx != -1)
            this.removeAt(idx);
        return idx != -1;
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        for (final Object obj : c)
            if (!this.contains(obj))
                return false;
        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends T> c) {
        boolean result = false;
        for (final T element : c) result |= this.add(element);
        return result;
    }

    public boolean addAll(final T[] list) {
        boolean result = false;
        for (final T t : list) result |= this.add(t);
        return result;
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return this.removeIf(c::contains);
    }

    @Override
    public boolean retainAll(@NotNull final Collection<?> c) {
        return this.removeIf(entry -> !c.contains(entry));
    }

    /**
     * Shrinks the backing array to the number of elements.
     */
    public void trim() {
        if (this.insertionIdx < this.data.length)
            this.data = this.insertionIdx == 0 ? EMPTY_ARRAY : Arrays.copyOf(this.data, this.insertionIdx);
    }

    @Override
    public void clear() {
        Arrays.fill(this.data, 0, this.insertionIdx, null);
        this.insertionIdx = 0;
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        return new Iterator<>() {

            private int idx = 0;

            @Override
            public boolean hasNext() {
                return this.idx < ObjectArray.this.insertionIdx;
            }

            @SuppressWarnings("unchecked")
            @Override
            public T next() {
                if (!this.hasNext())
                    throw new NoSuchElementException();
                return (T) ObjectArray.this.data[this.idx++];
            }
        };
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("[");
        for (int i = 0; i < this.insertionIdx; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(this.data[i] == this ? "(this ObjectArray)" : this.data[i]);
        }
        return sb.append(']').toString();
    }
}
