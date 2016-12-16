package org.ddd4j.infrastructure.scheduler;

import org.ddd4j.value.Throwing.TCloseable;

@FunctionalInterface
public interface HotSource<T> {

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