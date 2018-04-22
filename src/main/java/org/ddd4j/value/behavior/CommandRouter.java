package org.ddd4j.value.behavior;

import java.util.function.Function;
import java.util.function.Predicate;

import org.ddd4j.Require;
import org.ddd4j.Throwing;

@FunctionalInterface
public interface CommandRouter<T, C> {

	@FunctionalInterface
	interface BehaviorHandler<I, C, O> {

		Behavior<O> execute(I input, C command);

		default Behavior<O> map(Behavior<? extends I> behavior, C command) {
			return behavior.map(t -> execute(t, command));
		}

		default BehaviorHandler<C, I, O> swap() {
			return (c, t) -> execute(t, c);
		}
	}

	@FunctionalInterface
	interface EventsHandler<T, C> extends BehaviorHandler<T, C, T> {

		@Override
		default Behavior<T> execute(T target, C command) {
			Iterable<?> events = perform(target, command);
			// TODO Auto-generated method stub
			return Behavior.accept(callback, event);
		}

		Iterable<?> perform(T target, C command);
	}

	String MESSAGE_FORMAT_TEMPLATE = "Target '%s' can not handle: %s";

	static <T, M> CommandRouter<T, M> unhandled() {
		return unhandled(IllegalArgumentException::new);
	}

	static <T, M> CommandRouter<T, M> unhandled(Function<String, ? extends RuntimeException> exceptionFactory) {
		Throwing throwing = Throwing.of(exceptionFactory).withMessage(args -> String.format(MESSAGE_FORMAT_TEMPLATE, args));
		return throwing::throwUnchecked;
	}

	Behavior<? extends T> apply(Behavior<? extends T> behavior, C command);

	default Behavior<? extends T> applyAll(Behavior<? extends T> behavior, Iterable<? extends C> commands) {
		for (C command : commands) {
			behavior = apply(behavior, command);
		}
		return behavior;
	}

	default <X extends C> CommandRouter<T, C> when(Class<X> messageType, BehaviorHandler<? super T, ? super X, ? extends T> handler) {
		Require.nonNulls(messageType, handler);
		return (b, c) -> messageType.isInstance(c) ? handler.map(b, messageType.cast(c)) : apply(b, c);
	}

	default CommandRouter<T, C> when(Predicate<? super C> filter, BehaviorHandler<? super T, ? super C, ? extends T> handler) {
		Require.nonNulls(filter, handler);
		return (b, c) -> filter.test(c) ? handler.map(b, c) : apply(b, c);
	}

	default <X extends C> CommandRouter<T, C> when(Predicate<? super C> filter, Function<? super C, X> mapper,
			BehaviorHandler<? super T, ? super X, ? extends T> handler) {
		Require.nonNulls(filter, mapper, handler);
		return (b, c) -> filter.test(c) ? handler.map(b, mapper.apply(c)) : apply(b, c);
	}
}
