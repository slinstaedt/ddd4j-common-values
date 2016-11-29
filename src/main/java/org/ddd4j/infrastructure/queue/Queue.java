package org.ddd4j.infrastructure.queue;

import java.util.Collection;
import java.util.Optional;

public interface Queue<E> {

	@FunctionalInterface
	interface Batch<E> extends AutoCloseable {

		void add(E value);

		@Override
		default void close() {
		}
	}

	@FunctionalInterface
	interface Consumer<E> extends AutoCloseable {

		@Override
		default void close() {
		}

		E next();

		default Optional<E> nextOptional() {
			return Optional.ofNullable(next());
		}
	}

	@FunctionalInterface
	interface Producer<E> extends AutoCloseable {

		@Override
		default void close() {
		}

		default Batch<E> nextBatch() {
			return nextBatch(1);
		}

		Batch<E> nextBatch(int n);

		default void publish(E value) {
			try (Batch<E> batch = nextBatch()) {
				batch.add(value);
			}
		}

		default void publishAll(Collection<? extends E> values) {
			try (Batch<E> batch = nextBatch(values.size())) {
				values.forEach(batch::add);
			}
		}
	}

	Consumer<E> consumer();

	int getBufferSize();

	Producer<E> producer();
}
