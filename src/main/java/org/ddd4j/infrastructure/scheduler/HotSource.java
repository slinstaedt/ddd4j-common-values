package org.ddd4j.infrastructure.scheduler;

import org.ddd4j.value.Throwing;

@FunctionalInterface
public interface HotSource<T> {

	@FunctionalInterface
	interface Consumer<T> {

		void accept(T value) throws Exception;
	}

	@FunctionalInterface
	interface Subscription extends AutoCloseable {

		default void cancel() {
			try {
				close();
			} catch (Exception e) {
				Throwing.unchecked(e);
			}
		}
	}

	Subscription subscribe(Consumer<? super T> consumer);
}