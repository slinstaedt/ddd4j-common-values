package org.ddd4j.collection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.IntFunction;
import java.util.stream.Stream;

public class Array<E> implements Iterable<E> {

	private final ArrayList<E> elements;

	public Array() {
		this(10);
	}

	public Array(int initialCapacity) {
		this.elements = new ArrayList<>(initialCapacity);
	}

	public Array<E> add(E element) {
		elements.add(element);
		return this;
	}

	public void clear() {
		elements.clear();
	}

	public E get(int index) {
		return elements.get(index);
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
	public Iterator<E> iterator() {
		return elements.iterator();
	}

	public Array<E> set(int index, E element) {
		elements.ensureCapacity(index);
		int missing = index - elements.size();
		for (int i = 0; i < missing; i++) {
			elements.add(null);
		}
		elements.set(index, element);
		return this;
	}

	public int size() {
		return elements.size();
	}

	public Stream<E> stream() {
		return elements.stream();
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

	public Array<E> trimToSize() {
		elements.trimToSize();
		return this;
	}
}