package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

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

	default <T> Publisher<T> createLazyPublisher(PublisherType type, TSupplier<? extends Resource<T>> factory) {
		return createPublisher(type, Resource.lazy(factory));
	}

	default <T> Publisher<T> createPublisher(TSupplier<Source.Cold<T>> coldSource) {

	}

	default <T> Publisher<T> createPublisher(PublisherType type, Resource<? extends T> resource) {
		type.create(this, null);
		ScheduledPublisher<T> processor = new ScheduledPublisher<>(this);
		processor.onSubscribe(new ResourceSubscription<>(processor, resource));
		return processor;
	}

	default int getBurstProcessing() {
		return Integer.MAX_VALUE;
	}

	default <T> CompletionStage<T> supplyAsyncResult(TSupplier<T> supplier) {
		return CompletableFuture.supplyAsync(supplier, this);
	}

	default <T> CompletableResult<T> wrap(CompletionStage<T> stage) {
		return new CompletableResult<>(this, stage);
	}
}
