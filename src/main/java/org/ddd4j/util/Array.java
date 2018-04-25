package org.ddd4j.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;

public class Array<E> implements Iterable<E>, Sequence<E> {

	private final ArrayList<E> elements;

	public Array() {
		this(-1);
	}

	public Array(Array<? extends E> copy) {
		this.elements = new ArrayList<>(copy.elements);
	}

	public Array(int initialCapacity) {
		this.elements = initialCapacity >= 0 ? new ArrayList<>(initialCapacity) : new ArrayList<>();
	}

	public Array<E> add(E element) {
		elements.add(element);
		return this;
	}

	@SafeVarargs
	public final Array<E> addAll(E... elements) {
		return addAll(Arrays.asList(elements));
	}

	public Array<E> addAll(Iterable<? extends E> elements) {
		elements.forEach(this::add);
		return this;
	}

	public Array<E> addAll(Iterator<? extends E> iterator) {
		iterator.forEachRemaining(this::add);
		return this;
	}

	public void clear() {
		elements.clear();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		} else if (obj == null) {
			return false;
		} else if (getClass() != obj.getClass()) {
			return false;
		} else {
			Array<?> other = (Array<?>) obj;
			return this.elements.equals(other.elements);
		}
	}

	public E get(int index) {
		return elements.get(index);
	}

	public Optional<E> getOptional(int index) {
		return size() > index ? Optional.of(get(index)) : Optional.empty();
	}

	public E getOrAdd(int index, IntFunction<E> factory) {
		E element = getOrDefault(index, null);
		if (element == null) {
			element = factory.apply(index);
			set(index, element);
		}
		return element;
	}

	public E getOrDefault(int index, E defaultElement) {
		if (index < elements.size()) {
			E e = elements.get(index);
			return e != null ? e : defaultElement;
		} else {
			return defaultElement;
		}
	}

	@Override
	public int hashCode() {
		return elements.hashCode();
	}

	@Override
	public Iterator<E> iterator() {
		return elements.iterator();
	}

	public boolean remove(E element) {
		return elements.remove(element);
	}

	public Array<E> removeAll(Iterable<? extends E> elements) {
		elements.forEach(this::remove);
		return this;
	}

	public E set(int index, E element) {
		elements.ensureCapacity(index);
		int missing = index - elements.size();
		for (int i = 0; i < missing; i++) {
			elements.add(null);
		}
		return elements.set(index, element);
	}

	@Override
	public int size() {
		return elements.size();
	}

	@Override
	public Stream<E> stream() {
		return elements.stream();
	}

	public <T> Array<T> streamed(Function<Stream<E>, Stream<? extends T>> streamer) {
		return new Array<T>(size()).addAll(streamer.apply(stream()).iterator());
	}

	public Object[] toArray() {
		return elements.toArray();
	}

	public <T> T[] toArray(T[] a) {
		return elements.toArray(a);
	}

	@Override
	public String toString() {
		return elements.toString();
	}

	@SafeVarargs
	public final Array<E> with(E... elements) {
		return with(Arrays.asList(elements));
	}

	public Array<E> with(Iterable<? extends E> elements) {
		return elements.spliterator().getExactSizeIfKnown() != 0 ? new Array<>(this).addAll(elements) : this;
	}

	public Array<E> with(Iterator<? extends E> iterator) {
		return iterator.hasNext() ? new Array<>(this).addAll(iterator) : this;
	}
}
