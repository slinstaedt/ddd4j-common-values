package org.ddd4j.infrastructure.queue;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing.Task;
import org.ddd4j.value.Throwing.TConsumer;

public interface Queue<E> {

	interface Batch<E> extends AutoCloseable {

		void add(E value);

		@Override
		default void close() {
		}
	}

	interface Consumer<E> extends AutoCloseable {

		default int consumeAll(TConsumer<? super E> consumer) {
			int consumed = 0;
			E entry;
			while ((entry = next()) != null) {
				consumer.accept(entry);
				consumed++;
			}
			return consumed;
		}

		default Runnable createAsyncConsumer(TConsumer<? super E> consumer, Task start, IntConsumer finish, TConsumer<Exception> failed) {
			Require.nonNull(consumer);
			return () -> {
				try {
					start.run();
					int consumed = consumeAll(consumer);
					finish.accept(consumed);
				} catch (Exception e) {
					failed.accept(e);
				}
			};
		}

		E next();

		default Optional<E> nextOptional() {
			return Optional.ofNullable(next());
		}

		default Task scheduleConsumptionOnEnqueue(Executor executor) {

		}
	}

	@FunctionalInterface
	interface Producer<E> extends AutoCloseable {

		@Override
		default void close() throws Exception {
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
