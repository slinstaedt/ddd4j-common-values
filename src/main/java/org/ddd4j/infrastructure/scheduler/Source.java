package org.ddd4j.infrastructure.scheduler;

import org.ddd4j.value.Throwing.TCloseable;
import org.ddd4j.value.collection.Seq;

public interface Source<T> extends TCloseable {

	@FunctionalInterface
	interface Cold<T> extends Source<T> {

		Seq<? extends T> request(int n) throws Exception;
	}

	@FunctionalInterface
	interface Hot<T> extends Source<T> {

		@FunctionalInterface
		interface Consumer<T> {

			void accept(T value) throws Exception;
		}

		@FunctionalInterface
		interface Subscription extends TCloseable {

			default void cancel() {
				close();
			}
		}

		Subscription subscribe(Consumer<? super T> consumer);
	}

	@Override
	default void closeChecked() throws Exception {
	}
}
