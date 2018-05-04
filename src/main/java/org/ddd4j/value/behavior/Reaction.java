package org.ddd4j.value.behavior;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing;
import org.ddd4j.value.Either;
import org.ddd4j.value.behavior.Reaction.Accepted;
import org.ddd4j.value.behavior.Reaction.Rejected;
import org.ddd4j.value.collection.Seq;

public interface Reaction<T> extends Either<Accepted<T>, Rejected<T>> {

	class Accepted<T> implements Reaction<T> {

		private final Session session;
		private final T result;
		private final Seq<?> events;

		private Accepted(Session session, T result, Seq<?> events) {
			this.session = Require.nonNull(session);
			this.result = Require.nonNull(result);
			this.events = Require.nonNull(events);
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

		public Session getSession() {
			return session;
		}
	}

	class Rejected<T> implements Reaction<T> {

		private final Session session;
		private final String message;
		private final Object[] arguments;

		private Rejected(Session session, String message, Object... arguments) {
			this.session = Require.nonNull(session);
			this.message = Require.nonNull(message);
			this.arguments = Require.nonNull(arguments);
		}

		public <X> Rejected<X> casted() {
			return new Rejected<>(session, message, arguments);
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

		public Session getSession() {
			return session;
		}
	}

	static <T> Reaction<T> accepted(Session session, T result, Seq<?> events) {
		return new Accepted<>(session, result, events);
	}

	static <T> Reaction<T> failed(Session session, Exception exception) {
		return new Rejected<>(session, exception.getMessage(), exception);
	}

	static <T> Reaction<T> none(Session session, T result) {
		return new Accepted<>(session, result, Seq.empty());
	}

	static <T> Reaction<T> rejected(Session session, String message, Object... arguments) {
		return new Rejected<>(session, message, arguments);
	}

	Seq<?> events();

	default <X, Y> Either<X, Y> foldEither(Function<? super T, X> accepted, BiFunction<String, Object[], Y> rejected) {
		return foldReaction(accepted.andThen(Either::left), rejected.andThen(Either::right));
	}

	default <X> X foldReaction(Function<? super T, ? extends X> accepted, BiFunction<String, Object[], ? extends X> rejected) {
		return fold(a -> accepted.apply(a.getResult()), r -> rejected.apply(r.getMessage(), r.getArguments()));
	}

	default <X> Behavior<X> mapBehavior(Function<? super T, Behavior<X>> behavior) {
		return foldReaction(t -> {
			try {
				return behavior.apply(t);
			} catch (Exception e) {
				return Behavior.failed(e);
			}
		}, Behavior::reject);
	}

	default <X> Reaction<X> mapResult(Function<? super T, ? extends X> mapper) {
		return fold(a -> new Accepted<>(session(), mapper.apply(a.getResult()), a.events()), Rejected::casted);
	}

	default T result() {
		return fold(Accepted::getResult, Throwing.of(IllegalStateException::new).asFunction());
	}

	default Session session() {
		return fold(Accepted::getSession, Rejected::getSession);
	}
}
