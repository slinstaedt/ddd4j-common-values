package org.ddd4j.collection;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.ddd4j.Require;
import org.ddd4j.Throwing.Closeable;
import org.ddd4j.Throwing.Producer;
import org.ddd4j.Throwing.TConsumer;

public class Lazy<T> implements Closeable, Supplier<T> {

	private final Producer<? extends T> creator;
	private final TConsumer<? super T> destroyer;
	private final AtomicReference<T> reference;

	public Lazy(Producer<? extends T> creator) {
		this(creator, t -> {
		});
	}

	public Lazy(Producer<? extends T> creator, TConsumer<? super T> destroyer) {
		this.creator = Require.nonNull(creator);
		this.destroyer = Require.nonNull(destroyer);
		this.reference = new AtomicReference<>();
	}

	public Stream<T> asStream() {
		return Stream.of(get());
	}

	@Override
	public void closeChecked() {
		destroy(destroyer);
	}

	public void destroy() {
		destroy(destroyer);
	}

	public void destroy(TConsumer<? super T> destroyer) {
		T value = reference.getAndSet(null);
		if (value != null) {
			destroyer.accept(value);
		}
	}

	@Override
	public T get() {
		T value = reference.get();
		if (value == null) {
			value = reference.updateAndGet(t -> t != null ? t : creator.get());
		}
		return value;
	}

	public void ifPresent(TConsumer<? super T> consumer) {
		T value = reference.get();
		if (value != null) {
			consumer.accept(value);
		}
	}

	public boolean isInitialized() {
		return reference.get() != null;
	}
}