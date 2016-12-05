package org.ddd4j.infrastructure.scheduler;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.ddd4j.contract.Require;

public class Actor<M> {

	private final Executor executor;
	private final int burst;
	private final Consumer<? super M> messageHandler;
	private final BiFunction<? super M, ? super Exception, Optional<M>> errorHandler;
	private final Queue<M> messages;
	private final AtomicBoolean scheduled;

	public Actor(Executor executor, int burst, Consumer<? super M> messageHandler, BiFunction<? super M, ? super Exception, Optional<M>> errorHandler) {
		this.executor = Require.nonNull(executor);
		this.burst = Require.that(burst, burst > 0);
		this.messageHandler = Require.nonNull(messageHandler);
		this.errorHandler = Require.nonNull(errorHandler);
		this.messages = new ConcurrentLinkedQueue<>();
		this.scheduled = new AtomicBoolean(false);
	}

	public void handle(M message) {
		messages.offer(Require.nonNull(message));
		if (!scheduled.getAndSet(true)) {
			executor.execute(this::run);
		}
	}

	private void run() {
		M message = null;
		try {
			for (int i = 0; i < burst && (message = messages.poll()) != null; i++) {
				messageHandler.accept(message);
			}
		} catch (Exception e) {
			errorHandler.apply(message, e).ifPresent(this::handle);
		} finally {
			if (messages.isEmpty()) {
				scheduled.set(false);
			} else {
				executor.execute(this::run);
			}
		}
	}
}
