package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.ddd4j.value.Throwing.TSupplier;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

@FunctionalInterface
public interface Scheduler extends Executor {

	default <T> Publisher<T> asyncPublisher(Publisher<T> publisher) {
		return new ScheduledPublisher<>(invokeAsync(publisher));
	}

	default <T> Subscriber<T> asyncSubscriber(Subscriber<T> subscriber) {
		return new ScheduledSubscriber<>(invokeAsync(subscriber));
	}

	default <T> Publisher<T> createLazyPublisher(TSupplier<? extends Resource<T>> factory) {
		return createPublisher(Resource.lazy(factory));
	}

	default <T> Publisher<T> createPublisher(Resource<? extends T> resource) {
		ScheduledProcessor<T> processor = new ScheduledProcessor<>(this);
		processor.onSubscribe(new ResourceSubscription<>(processor, resource));
		return processor;
	}

	default <T> Agent<T> invokeAsync(T value) {
		return Agent.create(this, value);
	}

	default <T> CompletionStage<T> supplyAsyncResult(TSupplier<T> supplier) {
		return CompletableFuture.supplyAsync(supplier, this);
	}

	default <T> ScheduledResult<T> wrap(CompletionStage<T> stage) {
		return new ScheduledResult<>(this, stage);
	}
}
