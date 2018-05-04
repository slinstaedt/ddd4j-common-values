package org.ddd4j.util;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.ddd4j.util.Throwing.Closeable;
import org.ddd4j.util.Throwing.Producer;
import org.ddd4j.util.Throwing.TConsumer;
import org.ddd4j.util.Throwing.TFunction;

public class Lazy<T> implements Closeable, Supplier<T> {

	public static <T> Lazy<T> of(Producer<? extends T> creator) {
		return new Lazy<>(creator);
	}

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

	public <X> Consumer<X> asConsumer() {
		return x -> get();
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

	public <X> X ifPresent(TFunction<? super T, ? extends X> function, Supplier<X> defaultValue) {
		T value = reference.get();
		if (value != null) {
			return function.apply(value);
		} else {
			return defaultValue.get();
		}
	}

	public <X> X ifPresent(TFunction<? super T, ? extends X> function, X defaultValue) {
		return ifPresent(function, () -> defaultValue);
	}

	public boolean isInitialized() {
		return reference.get() != null;
	}
}
