package org.ddd4j.infrastructure.queue;

import java.util.Collection;
import java.util.Optional;

public interface Queue<E> {

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

		Transaction<E> nextEntries(int n);

		default Transaction<E> nextEntry() {
			return nextEntries(1);
		}

		default void publish(E value) {
			try (Transaction<E> tx = nextEntry()) {
				tx.add(value);
			}
		}

		default void publishAll(Collection<? extends E> values) {
			try (Transaction<E> tx = nextEntries(values.size())) {
				values.forEach(tx::add);
			}
		}
	}

	@FunctionalInterface
	interface Transaction<E> extends AutoCloseable {

		void add(E value);

		@Override
		default void close() {
		}
	}

	Consumer<E> consumer();

	Producer<E> producer();
}
