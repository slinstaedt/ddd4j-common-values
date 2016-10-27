package org.ddd4j.value.behavior;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Either;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.behavior.Reaction.Accepted;
import org.ddd4j.value.behavior.Reaction.Rejected;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.collection.Tpl;

public interface Reaction<T> extends Either<Accepted<T>, Rejected<T>> {

	class Accepted<T> implements Reaction<T> {

		private final T result;
		private final Seq<?> events;

		private Accepted(T result, Seq<?> events) {
			this.result = Require.nonNull(result);
			this.events = Require.nonNull(events);
		}

		private <X> Accepted<Tpl<T, X>> combine(Accepted<X> other) {
			return new Accepted<>(Tpl.of(this.result, other.result), this.events.appendAny().seq(other.events));
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

		private Rejected(String message, Object... arguments) {
			this.message = Require.nonNull(message);
			this.arguments = Require.nonNull(arguments);
		}

		public <X> Rejected<X> casted() {
			return new Rejected<>(message, arguments);
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

	static <T> Reaction<T> accepted(T result, Seq<?> events) {
		return new Accepted<>(result, events);
	}

	static <T> Reaction<T> failed(Exception exception) {
		return new Rejected<>(exception.getMessage(), exception);
	}

	static <T> Reaction<T> none(T result) {
		return new Accepted<>(result, Seq.empty());
	}

	static <T> Reaction<T> rejected(String message, Object... arguments) {
		return new Rejected<>(message, arguments);
	}

	default <X> Reaction<Tpl<T, X>> and(Reaction<X> other) {
		return fold(at -> other.fold(ax -> at.combine(ax), Rejected::casted), Rejected::casted);
	}

	Seq<?> events();

	default <X, Y> Either<X, Y> foldEither(Function<? super T, X> accepted, BiFunction<String, Object[], Y> rejected) {
		return foldReaction(accepted.andThen(Either::left), rejected.andThen(Either::right));
	}

	default <X> X foldReaction(Function<? super T, ? extends X> accepted, BiFunction<String, Object[], ? extends X> rejected) {
		return fold(a -> accepted.apply(a.getResult()), r -> rejected.apply(r.getMessage(), r.getArguments()));
	}

	default <X> Behavior<X> mapBehavior(Function<? super T, Behavior<X>> behavior) {
		return foldReaction(behavior, Behavior::reject);
	}

	default <X> Reaction<X> mapResult(Function<? super T, ? extends X> mapper) {
		return fold(a -> new Accepted<>(mapper.apply(a.getResult()), a.events()), Rejected::casted);
	}

	default <X> Reaction<Either.OrBoth<T, X>> or(Reaction<X> other) {
		return fold(at -> other.fold(ax -> {
			return at.combine(ax).mapResult(Either.OrBoth::both);
		}, rx -> {
			return at.<OrBoth<T, X>>mapResult(Either.OrBoth::left);
		}), rt -> other.fold(ax -> {
			return ax.<OrBoth<T, X>>mapResult(Either.OrBoth::right);
		}, Rejected::casted));
	}

	default T result() {
		return fold(Accepted::getResult, Throwing.of(IllegalStateException::new).asFunction());
	}

	default <X> Reaction<Either<T, X>> xor(Reaction<X> other) {
		return or(other).<Reaction<Either<T, X>>>fold(a -> a.getResult().fold(Reaction::none, tpl -> rejected("both maptch", tpl)), Rejected::casted);
	}
}
