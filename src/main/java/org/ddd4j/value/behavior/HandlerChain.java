package org.ddd4j.value.behavior;

import java.util.Optional;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.collection.Seq;

@FunctionalInterface
public interface HandlerChain<T, M> {

	@FunctionalInterface
	interface FactoryHandler<T, M> extends Handler<T, M, T> {

		T create(M message);

		default <ID> Function<ID, T> createFactory(Function<ID, ? extends M> messageMapper) {
			return messageMapper.andThen(this::create);
		}

		@Override
		default T handle(T target, M message) {
			if (target == null) {
				return create(message);
			} else {
				throw new IllegalStateException("Could not handle " + message + " for existing " + target);
			}
		}
	}

	@FunctionalInterface
	interface Handler<I, M, O> {

		default Behavior<O> applyBehavior(I target, M message) {
			try {
				O result = handle(target, message);
				return Behavior.accept(result, message);
			} catch (Exception e) {
				return Behavior.reject(e.getMessage());
			}
		}

		O handle(I target, M message);

		default Handler<M, I, O> swap() {
			return (m, i) -> handle(i, m);
		}
	}

	@FunctionalInterface
	interface ReferenceHandler<T, M> extends Handler<T, M, T> {

		@Override
		default T handle(T target, M message) {
			handleChange(target, message);
			return target;
		}

		void handleChange(T target, M message);
	}

	String MESSAGE_FORMAT_TEMPLATE = "Message '%s' does not apply to %s";

	static <T, M> HandlerChain<T, M> unhandled() {
		return unhandled(IllegalArgumentException::new);
	}

	static <T, M> HandlerChain<T, M> unhandled(Function<String, ? extends RuntimeException> exceptionFactory) {
		return Throwing.of(exceptionFactory).withMessage(args -> String.format(MESSAGE_FORMAT_TEMPLATE, args)).<T, M, T>asBiFunction()::apply;
	}

	default Behavior<T> applyBehavior(T target, M message) {
		try {
			T result = handle(target, message);
			return Behavior.accept(result, message);
		} catch (Exception e) {
			return Behavior.reject(e);
		}
	}

	default <X extends T, Y extends M> HandlerChain<T, M> chain(Class<X> targetType, Class<Y> messageType, Handler<? super X, ? super Y, ? extends T> handler) {
		Require.nonNullElements(targetType, messageType, handler);
		return (t, m) -> {
			if (targetType.isInstance(t) && messageType.isInstance(m)) {
				return handler.handle(targetType.cast(t), messageType.cast(m));
			} else {
				return this.handle(t, m);
			}
		};
	}

	default <Y extends M> HandlerChain<T, M> chain(Class<Y> messageType, Handler<? super T, ? super Y, ? extends T> handler) {
		Require.nonNullElements(messageType, handler);
		return (t, m) -> {
			if (messageType.isInstance(m)) {
				return handler.handle(t, messageType.cast(m));
			} else {
				return this.handle(t, m);
			}
		};
	}

	default <Y extends M> HandlerChain<T, M> chainFactory(Class<Y> messageType, FactoryHandler<T, ? super Y> handler) {
		return chain(messageType, handler);
	}

	default <X extends T, Y extends M> HandlerChain<T, M> chainReference(Class<X> targetType, Class<Y> messageType, ReferenceHandler<X, ? super Y> handler) {
		return chain(targetType, messageType, handler);
	}

	default <Y extends M> HandlerChain<T, M> chainReference(Class<Y> messageType, ReferenceHandler<T, ? super Y> handler) {
		return chain(messageType, handler);
	}

	default T createFrom(@SuppressWarnings("unchecked") M... messages) {
		Seq<M> sequence = Seq.of(messages);
		if (sequence.isEmpty()) {
			throw new IllegalArgumentException("No messages given");
		}
		return createFrom(sequence).orElseThrow(AssertionError::new);
	}

	default Optional<T> createFrom(Seq<? extends M> messages) {
		return messages.fold().eachWithIntitial(msg -> handle(null, msg), this::handle);
	}

	T handle(T target, M message);

	default T handleAll(T target, Seq<? extends M> messages) {
		return messages.fold().eachWithIdentity(target, this::handle);
	}

	default <X extends T, Y extends M> HandlerChain<T, M> swap(Class<X> targetType, Class<Y> messageType, Handler<? super Y, ? super X, ? extends T> handler) {
		return chain(targetType, messageType, handler.swap());
	}

	default <Y extends M> HandlerChain<T, M> swap(Class<Y> messageType, Handler<? super Y, ? super T, ? extends T> handler) {
		return chain(messageType, handler.swap());
	}

	default <X extends T, Y extends M> HandlerChain<T, M> swapReference(Class<X> targetType, Class<Y> messageType, ReferenceHandler<X, ? super Y> handler) {
		return chain(targetType, messageType, handler);
	}

	default <Y extends M> HandlerChain<T, M> swapReference(Class<Y> messageType, ReferenceHandler<T, ? super Y> handler) {
		return chain(messageType, handler);
	}
}
