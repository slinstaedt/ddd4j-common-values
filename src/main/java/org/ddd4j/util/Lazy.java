package org.ddd4j.util;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.ddd4j.Require;
import org.ddd4j.Throwing.Closeable;
import org.ddd4j.Throwing.Producer;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.Throwing.TFunction;

public class Lazy<T> implements Closeable, Supplier<T> {

	public static <T extends AutoCloseable> Lazy<T> ofCloseable(Producer<? extends T> creator) {
		return new Lazy<>(creator, AutoCloseable::close);
	}

	private final Producer<? extends T> creator;
	private final TConsumer<? super T> destroyer;
	private final AtomicReference<T> reference;

	public Lazy(Producer<? extends T> creator) {
		this(creator, Object::getClass);
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
		destroy();
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

	public <X> X ifPresent(TFunction<? super T, ? extends X> function, X defaultValue) {
		T value = reference.get();
		if (value != null) {
			return function.apply(value);
		} else {
			return defaultValue;
		}
	}

	public boolean isInitialized() {
		return reference.get() != null;
	}
}
