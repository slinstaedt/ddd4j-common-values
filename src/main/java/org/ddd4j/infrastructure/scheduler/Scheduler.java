package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import org.ddd4j.spi.Service;
import org.ddd4j.value.Throwing.TSupplier;
import org.ddd4j.value.Type;
import org.reactivestreams.Publisher;

@FunctionalInterface
public interface Scheduler extends Executor, Service<Scheduler, SchedulerProvider> {

	default <T> Actor<T> createActor(T initialState) {
		return Actor.create(this, initialState);
	}

	default <T> T createActorDecorator(Type<T> type, T initialState) {
		return ActorInvocationHandler.create(this, type, initialState);
	}

	default <T> Publisher<T> createPublisher(ColdSource<T> source) {
		return new ScheduledPublisher<>(this, new ColdPublisher<>(source));
	}

	default <T> CompletableResult<T> createResult(CompletionStage<T> stage) {
		return CompletableResult.of(this, stage);
	}

	default <T> CompletableResult<T> createResult(Future<T> future) {
		return CompletableResult.ofWaiting(this, future);
	}

	default <T> CompletableResult<T> createResult(TSupplier<T> supplier) {
		return CompletableResult.ofLazy(this, supplier);
	}

	default int getBurstProcessing() {
		return Integer.MAX_VALUE;
	}
}
