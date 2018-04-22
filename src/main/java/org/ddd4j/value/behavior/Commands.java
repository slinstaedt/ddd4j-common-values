package org.ddd4j.value.behavior;

import java.util.function.Function;

import org.ddd4j.aggregate.Aggregates;

public class Commands {

	@FunctionalInterface
	public interface Modify<T> {

		<X> X apply(Function<? super T, Behavior<X>> function);
	}

	interface EventStore {
	}

	private EventStore eventStore;
	private Aggregates aggregates;

	public <T> Modify<T> on(Identifier identifier) {
		return new Modify<T>() {

			@Override
			public <X> X apply(Function<? super T, Behavior<X>> function) {
				// TODO Auto-generated method stub
				return null;
			}
		};
	}
}
