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

		private final Seq<?> events;
		private final T result;

		private Accepted(Seq<?> events) {
			this.events = Require.nonNull(events);
			this.result = null;
		}

		private Accepted(Seq<?> events, T result) {
			this.events = Require.nonNull(events);
			this.result = Require.nonNull(result);
		}

		private Accepted(T result) {
			this(Seq.empty(), result);
		}

		private <X> Accepted<Tpl<T, X>> combine(Accepted<X> other) {
			return new Accepted<>(this.events.appendAny().seq(other.events), Tpl.of(this.result, other.result));
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

	static <T> Reaction<T> accepted(Seq<?> events) {
		return new Accepted<>(events);
	}

	static <T> Reaction<T> accepted(T result, Seq<?> events) {
		return new Accepted<>(events, result);
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

	default <X> Reaction<X> mapBehavior(Function<? super T, Behavior<X>> behavior) {
		return foldReaction(behavior, Behavior::<X>reject).applyEvents(events());
	}

	default <X> Reaction<X> mapResult(Function<? super T, ? extends X> mapper) {
		return fold(a -> new Accepted<>(a.events(), mapper.apply(a.getResult())), Rejected::casted);
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
		return or(other).<Reaction<Either<T, X>>>fold(a -> a.getResult().fold(Accepted<Either<T, X>>::new, tpl -> rejected("both maptch", tpl)),
				Rejected::casted);
	}
}
