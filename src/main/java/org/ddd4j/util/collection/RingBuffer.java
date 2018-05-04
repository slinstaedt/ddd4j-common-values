package org.ddd4j.util.collection;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.ddd4j.util.Require;

public class RingBuffer<E> implements Iterable<E>, Iterator<E> {

	public interface Holder<E> {

		class Array<E> implements Holder<E> {

			private final E[] elements;
			private final int offset;
			private final int length;
			private int current;

			public Array(E[] elements, int offset, int length) {
				this.elements = elements;
				this.offset = offset;
				this.length = length;
				this.current = 0;
			}

			@Override
			public boolean acceptsMore(E element) {
				elements[offset + current++] = element;
				return current == length;
			}

			@Override
			public void reinit() {
				current = 0;
			}

			public int size() {
				return current;
			}
		}

		class Single<E> implements Holder<E> {

			private E element;

			@Override
			public boolean acceptsMore(E element) {
				this.element = element;
				return false;
			}

			public E getOrElse(E other) {
				return element != null ? element : other;
			}

			public <X extends Throwable> E getOrThrow(Supplier<X> exception) throws X {
				if (element != null) {
					return element;
				} else {
					throw exception.get();
				}
			}
		}

		boolean acceptsMore(E element);

		default void reinit() {
		}
	}

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
		return get(new Holder.Single<>()).getOrThrow(NoSuchElementException::new);
	}

	public int get(E[] target) {
		return get(new Holder.Array<>(target, 0, target.length)).size();
	}

	public int get(E[] target, int offset, int length) {
		return get(new Holder.Array<>(target, offset, length)).size();
	}

	public <H extends Holder<? super E>> H get(H holder) {
		getIndex.getAndUpdate(i -> {
			holder.reinit();
			while (i != EMPTY && i != putIndex.get() && holder.acceptsMore(elements[i])) {
				i = nextIndex(i);
			}
			if (i == putIndex.get()) {
				i = EMPTY;
			}
			return i;
		});
		return holder;
	}

	public long getOffsetEnd() {
		return offset.get();
	}

	public long getOffsetStart() {
		return getOffsetEnd() - size();
	}

	public E getOrNull() {
		return get(new Holder.Single<>()).getOrElse(null);
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

	private int nextIndex(int current) {
		return (current + 1) % elements.length;
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
