package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Outcome;
import org.ddd4j.infrastructure.Outcome.CompletableOutcome;
import org.ddd4j.infrastructure.Result;
import org.ddd4j.spi.Service;
import org.ddd4j.value.Type;
import org.ddd4j.value.versioned.Revisions;

@FunctionalInterface
public interface Scheduler extends Executor, Service<Scheduler, SchedulerProvider> {

	default <T> Outcome<T> completedOutcome(T value) {
		return Outcome.ofCompleted(this, value);
	}

	default <T> Actor<T> createActor(T initialState) {
		return Actor.create(this, initialState);
	}

	default <T> T createActorDecorator(Type<T> type, T initialState) {
		return ActorInvocationHandler.create(this, type, initialState);
	}

	default <T> Outcome<T> createOutcome(Callable<T> callable) {
		return Outcome.ofEager(this, Require.nonNull(callable)::call);
	}

	default <T> Outcome<T> createOutcome(CompletionStage<T> stage) {
		return Outcome.of(this, stage);
	}

	default <T> Outcome<T> createOutcome(Future<T> future) {
		return Outcome.ofBlocking(this, future);
	}

	default <T> CompletableOutcome<T> createCompletableOutcome() {
		return Outcome.ofCompletable(this);
	}

	default <T> Result<T> createResult(ColdSource<T> source, Revisions startAt, boolean completeOnEnd) {
		return new ScheduledResult<>(this, new ColdResult<>(source, startAt.asLong(), completeOnEnd));
	}

	default <T> Outcome<T> failedOutcome(Throwable exception) {
		return Outcome.ofFailed(this, exception);
	}

	default int getBurstProcessing() {
		return Integer.MAX_VALUE;
	}
}
