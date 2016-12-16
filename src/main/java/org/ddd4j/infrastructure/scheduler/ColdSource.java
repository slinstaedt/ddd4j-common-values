package org.ddd4j.infrastructure.scheduler;

import org.ddd4j.value.Throwing.TCloseable;
import org.ddd4j.value.collection.Seq;

@FunctionalInterface
public interface ColdSource<T> {

	@FunctionalInterface
	interface Connection<T> extends TCloseable {

		@Override
		default void closeChecked() throws Exception {
		}

		Seq<? extends T> request(int n) throws Exception;
	}

	Connection<T> open() throws Exception;
}