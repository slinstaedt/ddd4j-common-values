package org.ddd4j.value.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.value.collection.Seq;

@FunctionalInterface
public interface Routing<T, M> {

	@FunctionalInterface
	interface EntityHandler<T, M> extends ValueHandler<T, M, T> {

		void apply(T target, M message);

		@Override
		default T handle(T target, M message) {
			apply(target, message);
			return target;
		}
	}

	@FunctionalInterface
	interface ValueHandler<I, M, O> {

		O handle(I input, M message);

		default ValueHandler<M, I, O> swap() {
			return (m, i) -> handle(i, m);
		}
	}

	String MESSAGE_FORMAT_TEMPLATE = "Target '%s' can not handle: %s";

	static <T, M> Routing<T, M> unhandled() {
		return unhandled(IllegalArgumentException::new);
	}

	static <T, M> Routing<T, M> unhandled(Function<String, ? extends RuntimeException> exceptionFactory) {
		Throwing throwing = Throwing.of(exceptionFactory).withMessage(args -> String.format(MESSAGE_FORMAT_TEMPLATE, args));
		return throwing::throwUnchecked;
	}

	default Optional<T> createFrom(Seq<? extends M> messages) {
		return Optional.ofNullable(handleAll(null, messages));
	}

	T handle(T target, M message);

	default T handleAll(T target, Seq<? extends M> messages) {
		return messages.fold().eachWithIdentity(target, this::handle);
	}

	default <X extends M> Routing<T, M> on(Class<X> messageType, EntityHandler<T, ? super X> handler) {
		return when(messageType, handler);
	}

	default Routing<T, M> on(Predicate<? super M> filter, EntityHandler<T, ? super M> handler) {
		return when(filter, handler);
	}

	default <X extends M> Routing<T, M> on(Predicate<? super M> filter, Function<? super M, X> mapper,
			EntityHandler<T, ? super X> handler) {
		return when(filter, mapper, handler);
	}

	default <X extends M> Routing<T, M> when(Class<X> messageType, ValueHandler<? super T, ? super X, ? extends T> handler) {
		Require.nonNulls(messageType, handler);
		return (t, m) -> messageType.isInstance(m) ? handler.handle(t, messageType.cast(m)) : handle(t, m);
	}

	default <X extends M> Routing<T, M> when(Predicate<? super M> filter, Function<? super M, X> mapper,
			ValueHandler<? super T, ? super X, ? extends T> handler) {
		Require.nonNulls(filter, mapper, handler);
		return (t, m) -> filter.test(m) ? handler.handle(t, mapper.apply(m)) : handle(t, m);
	}

	default Routing<T, M> when(Predicate<? super M> filter, ValueHandler<? super T, ? super M, ? extends T> handler) {
		Require.nonNulls(filter, handler);
		return (t, m) -> filter.test(m) ? handler.handle(t, m) : handle(t, m);
	}
}
