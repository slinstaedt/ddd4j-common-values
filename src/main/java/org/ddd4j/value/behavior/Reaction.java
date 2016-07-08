package org.ddd4j.value.behavior;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Either;
import org.ddd4j.value.behavior.Reaction.Accepted;
import org.ddd4j.value.behavior.Reaction.Rejected;
import org.ddd4j.value.collection.Seq;

public interface Reaction<T> extends Either<Accepted<T>, Rejected<T>> {

	class Accepted<T> implements Reaction<T> {

		private final Seq<?> events;
		private final T result;

		public Accepted(Seq<?> events) {
			this.events = Require.nonNull(events);
			this.result = null;
		}

		public Accepted(Seq<?> events, T result) {
			this.events = Require.nonNull(events);
			this.result = Require.nonNull(result);
		}

		@Override
		public Seq<?> events() {
			return events;
		}

		@Override
		public <X> X fold(Function<? super Accepted<T>, ? extends X> left, Function<? super Rejected<T>, ? extends X> right) {
			return left.apply(this);
		}

		public T getResult() {
			return result;
		}
	}

	class Rejected<T> implements Reaction<T> {

		private final String message;
		private final Object[] arguments;

		public Rejected(String message, Object... arguments) {
			this.message = Require.nonNull(message);
			this.arguments = Require.nonNull(arguments);
		}

		@Override
		public Seq<?> events() {
			return Seq.empty();
		}

		@Override
		public <X> X fold(Function<? super Accepted<T>, ? extends X> left, Function<? super Rejected<T>, ? extends X> right) {
			return right.apply(this);
		}

		public Object[] getArguments() {
			return arguments;
		}

		public String getMessage() {
			return message;
		}
	}

	Seq<?> events();

	default <X> X fold(Function<? super T, X> accepted, BiFunction<String, Object[], ? extends X> rejected) {
		return fold(a -> accepted.apply(a.getResult()), r -> rejected.apply(r.getMessage(), r.getArguments()));
	}

	default <X, Y> Either<X, Y> foldEither(Function<? super T, X> accepted, BiFunction<String, Object[], Y> rejected) {
		return fold(accepted.andThen(Either::left), rejected.andThen(Either::right));
	}

	default <X> Reaction<X> mapBehavior(Function<? super T, Behavior<X>> behavior) {
		return fold(behavior, Behavior::reject).applyEvents(events());
	}
}
