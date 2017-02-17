package org.ddd4j.value;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing.Closeable;
import org.ddd4j.value.Throwing.Producer;
import org.ddd4j.value.Throwing.TConsumer;
import org.ddd4j.value.collection.Seq;

public class Lazy<T> implements Closeable, Seq<T> {

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

	@Override
	public void closeChecked() {
		destroy(destroyer);
	}

	public void destroy() {
		destroy(destroyer);
	}

	public void destroy(Consumer<? super T> destroyer) {
		reference.updateAndGet(t -> {
			if (t != null) {
				destroyer.accept(t);
			}
			return null;
		});
	}

	public T get() {
		return reference.updateAndGet(t -> t != null ? t : creator.get());
	}

	@Override
	public Stream<T> stream() {
		return Stream.of(get());
	}
}
