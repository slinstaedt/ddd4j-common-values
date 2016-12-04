package org.ddd4j.infrastructure.queue;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing.TCloseable;
import org.ddd4j.value.Throwing.TConsumer;
import org.ddd4j.value.Throwing.TRunnable;

public interface Queue<E> {

	interface Batch<E> extends TCloseable {

		void add(E value);
	}

	interface Consumer<E> extends TCloseable {

		default int consumeAll(TConsumer<? super E> consumer) {
			int consumed = 0;
			E entry;
			while ((entry = next()) != null) {
				consumer.accept(entry);
				consumed++;
			}
			return consumed;
		}

		default Runnable createAsyncConsumer(TConsumer<? super E> consumer, TRunnable start, IntConsumer finish, TConsumer<Exception> failed) {
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

		default TCloseable scheduleConsumptionOnEnqueue(Executor executor) {

		}
	}

	@FunctionalInterface
	interface Producer<E> extends TCloseable {

		@Override
		default void closeChecked() throws Exception {
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
