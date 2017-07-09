package org.ddd4j.collection;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.ddd4j.Require;

public class RingBuffer<E> implements Iterable<E>, Iterator<E> {

	private static final int EMPTY = -1;

	private final E[] elements;
	private final AtomicInteger getIndex;
	private final AtomicInteger putIndex;
	private final AtomicLong offset;

	@SuppressWarnings("unchecked")
	public RingBuffer(int size) {
		Require.that(size > 0);
		this.elements = (E[]) new Object[size];
		this.getIndex = new AtomicInteger(EMPTY);
		this.putIndex = new AtomicInteger(0);
		this.offset = new AtomicLong(0);
	}

	public E get() throws NoSuchElementException {
		int index = getIndex.getAndUpdate(current -> {
			if (current == EMPTY) {
				throw new NoSuchElementException();
			}
			int next = nextIndex(current);
			if (next == putIndex.get()) {
				next = EMPTY;
			}
			return next;
		});
		return elements[index];
	}

	public long getOffsetEnd() {
		return offset.get();
	}

	public long getOffsetStart() {
		return getOffsetEnd() - size();
	}

	@Override
	public boolean hasNext() {
		return !isEmpty();
	}

	public boolean isEmpty() {
		return getIndex.get() == EMPTY;
	}

	public boolean isFull() {
		return getIndex.get() == putIndex.get();
	}

	@Override
	public Iterator<E> iterator() {
		return this;
	}

	@Override
	public E next() {
		return get();
	}

	private int nextIndex(int index) {
		return (index + 1) % elements.length;
	}

	public void put(E element) {
		Require.nonNull(element);
		int index = putIndex.getAndUpdate(this::nextIndex);
		elements[index] = element;
		getIndex.compareAndSet(index, nextIndex(index)); // drop last due to overflow
		getIndex.compareAndSet(EMPTY, index); // was empty before
		offset.getAndIncrement();
	}

	public void put(E element, long offset) {
		put(element);
		this.offset.set(offset);
	}

	public int size() {
		int get = getIndex.get();
		if (get == EMPTY) {
			return 0;
		}
		int size = putIndex.get() - get;
		if (size <= 0) {
			size += elements.length;
		}
		return size;
	}

	public Stream<E> stream() {
		return StreamSupport.stream(Spliterators.spliterator(this, size(), Spliterator.NONNULL | Spliterator.ORDERED), false);
	}
}
