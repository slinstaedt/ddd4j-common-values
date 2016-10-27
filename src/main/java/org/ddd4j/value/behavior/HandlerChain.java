package org.ddd4j.value.behavior;

import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Nothing;
import org.ddd4j.value.collection.Seq;

public class HandlerChain<T> {

	@FunctionalInterface
	public interface FactoryHandler<T, M> extends MessageHandler<Object, M, T> {

		T create(M message);

		default <ID> Function<ID, T> createFactory(Function<ID, ? extends M> messageMapper) {
			return messageMapper.andThen(this::create);
		}

		@Override
		default T apply(Object target, M message) {
			if (target == Nothing.INSTANCE) {
				return create(message);
			} else {
				throw new IllegalStateException("Could not handle " + message + " for existing " + target);
			}
		}

		default Behavior<T> record(M message) {
			return record(Nothing.INSTANCE, message);
		}
	}

	@FunctionalInterface
	public interface MessageHandler<S, M, T> {

		T apply(S target, M message);

		default Function<S, T> withMessage(M message) {
			return s -> apply(s, message);
		}

		default Function<M, T> withTarget(S target) {
			return m -> apply(target, m);
		}

		default MessageHandler<M, S, T> swap() {
			return (m, s) -> apply(s, m);
		}

		// TODO rename?
		default Behavior<T> record(S target, M message) {
			return Behavior.accept(m -> apply(target, m), message);
		}
	}

	@FunctionalInterface
	public interface ReferenceHandler<T, M> extends MessageHandler<T, M, T> {

		@Override
		default T apply(T target, M message) {
			handleChange(target, message);
			return target;
		}

		void handleChange(T target, M message);
	}

	private static class BehaviorHandler<B, T extends B, M> {

		private final Class<? extends T> targetType;
		private final Class<? extends M> messageType;
		private final MessageHandler<? super T, ? super M, ? extends B> handler;

		BehaviorHandler(Class<? extends T> targetType, Class<? extends M> messageType, MessageHandler<? super T, ? super M, ? extends B> handler) {
			this.targetType = Require.nonNull(targetType);
			this.messageType = Require.nonNull(messageType);
			this.handler = Require.nonNull(handler);
		}

		public Behavior<? extends B> apply(Behavior<? extends B> behavior, Object message) {
			if (messageType.isInstance(message)) {
				return behavior.map(t -> {
					// java compiler can not infer type of SAM expression
					Behavior<? extends B> result;
					if (targetType.isInstance(t)) {
						result = handler.record(targetType.cast(t), messageType.cast(message));
					} else {
						result = behavior;
					}
					return result;
				});
			} else {
				return behavior;
			}
		}

		public B applyFromHistory(T target, M event) {
			if (targetType.isInstance(target) && messageType.isInstance(event)) {
				return handler.apply(targetType.cast(target), messageType.cast(event));
			} else {
				// TODO
				return null;
			}
		}

		public Class<? extends M> messageType() {
			return messageType;
		}

		@Override
		public String toString() {
			return "Handler for target=" + targetType.getName() + ", message=" + messageType.getName();
		}
	}

	private static final String MESSAGE_FORMAT_TEMPLATE = "Message '%s' does not apply to %s";

	public static <T> HandlerChain<T> create(Class<T> baseType) {
		return new HandlerChain<>(baseType, Seq.empty());
	}

	private final Class<T> baseType;
	private final Seq<BehaviorHandler<T, ?, ?>> handlers;

	public HandlerChain(Class<T> baseType, Seq<BehaviorHandler<T, ?, ?>> handlers) {
		this.baseType = Require.nonNull(baseType);
		this.handlers = Require.nonNull(handlers);
	}

	public Behavior<? extends T> apply(T target, Object message) {
		Behavior<T> behavior = Behavior.none(target);
		return handlers.fold().eachWithIdentity(behavior, (b, h) -> {
			Behavior<T> applied = h.apply(b, message);
			return applied;
		});
	}

	public Behavior<T> applyCommand(T target, Object command) {
		// TODO check for multiple handlers
		throw new UnsupportedOperationException();
	}

	public Behavior<? extends T> applyEvent(T target, Object event) {
		return apply(target, event);
	}

	public T applyFromHistory(T target, Object event) {
		Behavior<T> behavior = Behavior.none(target);
		for (BehaviorHandler<? super T, ?, ?> handler : handlers) {
			// handler.apply(target, event);
		}
		return behavior.result();
	}

	public HandlerChain<T> failedOnUnhandled() {
		return failedOnUnhandled(IllegalArgumentException::new);
	}

	public <M> HandlerChain<T> failedOnUnhandled(Function<String, ? extends RuntimeException> exceptionFactory) {
		return null;// TODO Throwing.of(exceptionFactory).withMessage(args -> String.format(MESSAGE_FORMAT_TEMPLATE, args)).<T, M, T>asBiFunction()::apply;
	}

	public Seq<Class<?>> handledMessageTypes() {
		return handlers.map().to(BehaviorHandler::messageType);
	}

	public <X extends T, M> HandlerChain<T> when(Class<X> targetType, Class<M> messageType, MessageHandler<? super X, ? super M, ? extends T> handler) {
		return new HandlerChain<>(baseType, handlers.append().entry(new BehaviorHandler<>(targetType, messageType, handler)));
	}

	public <M> HandlerChain<T> when(Class<M> messageType, MessageHandler<? super T, ? super M, ? extends T> handler) {
		return when(baseType, messageType, handler);
	}

	public <M> HandlerChain<T> chainFactory(Class<M> messageType, FactoryHandler<? extends T, ? super M> handler) {
		return when(messageType, handler);
	}

	public <X extends T, M> HandlerChain<T> chainReference(Class<X> targetType, Class<M> messageType, ReferenceHandler<X, ? super M> handler) {
		return when(targetType, messageType, handler);
	}

	public <M> HandlerChain<T> chainReference(Class<M> messageType, ReferenceHandler<T, ? super M> handler) {
		return when(messageType, handler);
	}

	public <X extends T, M> HandlerChain<T> swap(Class<X> targetType, Class<M> messageType, MessageHandler<? super M, ? super X, ? extends X> handler) {
		return when(targetType, messageType, handler.swap());
	}

	public <M> HandlerChain<T> swap(Class<M> messageType, MessageHandler<? super M, ? super T, ? extends T> handler) {
		return when(messageType, handler.swap());
	}

	public <X extends T, M> HandlerChain<T> swapReference(Class<X> targetType, Class<M> messageType, ReferenceHandler<X, ? super M> handler) {
		return when(targetType, messageType, handler);
	}

	public <M> HandlerChain<T> swapReference(Class<M> messageType, ReferenceHandler<T, ? super M> handler) {
		return when(messageType, handler);
	}

	@Override
	public String toString() {
		return "Handler chain:" + handlers.asString();
	}
}
