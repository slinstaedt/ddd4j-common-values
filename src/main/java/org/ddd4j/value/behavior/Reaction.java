package org.ddd4j.value.behavior;

import static java.util.Objects.requireNonNull;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.ddd4j.value.collection.Seq;

public interface Reaction<T> {

	class Accepted<T> implements Reaction<T> {

		private final Seq<?> events;

		public Accepted(Seq<?> events) {
			this.events = requireNonNull(events);
		}

		@Override
		public Seq<?> events() {
			return events;
		}

		@Override
		public <X> X get(Function<? super T, X> accepted, BiFunction<String, Object[], X> rejected) {
			return null;
		}
	}

	class AcceptedWithResult<T> extends Accepted<T> {

		private final T result;

		public AcceptedWithResult(Seq<?> events, T result) {
			super(events);
			this.result = requireNonNull(result);
		}

		@Override
		public <X> X get(Function<? super T, X> accepted, BiFunction<String, Object[], X> rejected) {
			return accepted.apply(result);
		}
	}

	class Rejected<T> implements Reaction<T> {

		private final String message;
		private final Object[] arguments;

		public Rejected(String message, Object... arguments) {
			this.message = requireNonNull(message);
			this.arguments = requireNonNull(arguments);
		}

		@Override
		public Seq<?> events() {
			return Seq.empty();
		}

		@Override
		public <X> X get(Function<? super T, X> accepted, BiFunction<String, Object[], X> rejected) {
			return rejected.apply(message, arguments);
		}
	}

	default <X> Reaction<X> mapBehavior(Function<? super T, Behavior<X>> behavior) {
		return get(behavior, Behavior::reject).applyEvents(events());
	}

	Seq<?> events();

	<X> X get(Function<? super T, X> accepted, BiFunction<String, Object[], X> rejected);
}
