package org.ddd4j.infrastructure.scheduler;

import org.ddd4j.value.Throwing;

@FunctionalInterface
public interface Task<T> {

	T execute() throws Exception;

	default T executeUnchecked() {
		try {
			return execute();
		} catch (Exception e) {
			return Throwing.unchecked(e);
		}
	}
}
