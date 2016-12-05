package org.ddd4j.infrastructure.scheduler;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.ddd4j.spi.Service;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.Throwing.TSupplier;
import org.ddd4j.value.Type;
import org.reactivestreams.Publisher;

@FunctionalInterface
public interface Scheduler extends Executor, Service<Scheduler, SchedulerProvider> {

	default int getBurstProcessing() {
		return Integer.MAX_VALUE;
	}

	default <M> Actor<M> createActor(Consumer<? super M> messageHandler) {
		return createActor(messageHandler, (m, e) -> Throwing.unchecked(e));
	}

	default <M> Actor<M> createActor(Consumer<? super M> messageHandler, BiFunction<? super M, ? super Exception, Optional<M>> errorHandler) {
		return new Actor<>(this, getBurstProcessing(), messageHandler, errorHandler);
	}

	default <T> T createActorDecorator(Type<T> type, T delegate) {
		return ScheduledInvocationHandler.create(this, type, delegate);
	}

	default <T> Publisher<T> createLazyPublisher(PublisherType type, TSupplier<? extends Resource<T>> factory) {
		return createPublisher(type, Resource.lazy(factory));
	}

	default <T> Publisher<T> createPublisher(PublisherType type, Resource<? extends T> resource) {
		type.create(this, null);
		ScheduledProcessor<T> processor = new ScheduledProcessor<>(this);
		processor.onSubscribe(new ResourceSubscription<>(processor, resource));
		return processor;
	}

	default <T> CompletionStage<T> supplyAsyncResult(TSupplier<T> supplier) {
		return CompletableFuture.supplyAsync(supplier, this);
	}

	default <T> ScheduledResult<T> wrap(CompletionStage<T> stage) {
		return new ScheduledResult<>(this, stage);
	}
}
