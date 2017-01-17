package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import org.ddd4j.infrastructure.Outcome;
import org.ddd4j.spi.Service;
import org.ddd4j.value.Type;

@FunctionalInterface
public interface Scheduler extends Executor, Service<Scheduler, SchedulerProvider> {

	default <T> Outcome<T> completedOutcome(T value) {
		return Outcome.ofCompleted(this, value);
	}

	default <T> Agent<T> createAgent(T initialState) {
		return Agent.create(this, initialState);
	}

	default <T> T createAgentDecorator(Type<T> type, T initialState) {
		return ActorInvocationHandler.create(this, type, initialState);
	}

	default <T> Outcome<T> createOutcome(CompletionStage<T> stage) {
		return Outcome.of(this, stage);
	}

	default <T> Outcome<T> createOutcome(Future<T> future) {
		return Outcome.ofBlocking(this, future);
	}

	default <T> CompletableOutcome<T> createCompletableOutcome() {
		return new CompletableOutcome<>(this);
	}

	default <T> Outcome<T> failedOutcome(Throwable exception) {
		return Outcome.ofFailed(this, exception);
	}

	default int getBurstProcessing() {
		return Integer.MAX_VALUE;
	}
}
