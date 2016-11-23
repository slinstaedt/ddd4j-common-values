package org.ddd4j.stream;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class Broadcast<T> implements Processor<T, T> {

	private class Target implements Subscription, Runnable {

		private final Subscriber<? super T> subscriber;
		private final Queue<T> queue;
		private long requested;

		public Target(Subscriber<? super T> subscriber, long requested) {
			this.subscriber = Require.nonNull(subscriber);
			this.queue = new ConcurrentLinkedQueue<>();
			this.requested = requested;
		}

		@Override
		public void cancel() {
			cancelTarget(this);
		}

		void onComplete() {
			cancel();
			subscriber.onComplete();
		}

		void onError(Throwable exception) {
			cancel();
			subscriber.onError(exception);
		}

		void onNext(T value) {
			queue.offer(value);
		}

		@Override
		public void request(long n) {
			requested += n;
			if (requested < 0) {
				requested = Long.MAX_VALUE;
			}
			requestMoreIfNeeded();
		}

		@Override
		public void run() {
			T value = null;
			while ((value = queue.poll()) != null) {
				requested--;
				subscriber.onNext(value);
			}
		}
	}

	private static final Subscription NONE = new Subscription() {

		@Override
		public void cancel() {
		}

		@Override
		public void request(long n) {
		}
	};

	public static <T> Broadcast<T> create(LongFunction<CompletionStage<List<T>>> reader, AutoCloseable closer) {
		Broadcast<T> broadcast = new Broadcast<>();
		broadcast.registerCallback(reader, closer);
		return broadcast;
	}

	private final Executor executor;
	private final Set<SingleThreaded<Target>> subscriptions;
	private final AtomicLong alreadyRequested;
	private Subscription subscription;

	public Broadcast() {
		this(Runnable::run);
	}

	public Broadcast(Executor executor) {
		this.executor = Require.nonNull(executor);
		this.subscriptions = new CopyOnWriteArraySet<>();
		this.alreadyRequested = new AtomicLong(0);
		this.subscription = NONE;
	}

	private void cancelTarget(Target target) {
		if (subscriptions.remove(target) && subscriptions.isEmpty()) {
			executor.execute(subscription::cancel);
			subscription = NONE;
		}
	}

	private void requestMoreIfNeeded() {
		long already = alreadyRequested.get();
		long request = subscriptions.stream().mapToLong(t -> t.getDelegate().requested).map(r -> r - already).min().orElse(0);
		if (request > 0 && alreadyRequested.compareAndSet(already, already + request)) {
			executor.execute(() -> subscription.request(request));
		}
	}

	@Override
	public void onComplete() {
		subscriptions.stream().map(SingleThreaded::getDelegate).forEach(t -> t.onComplete());
	}

	@Override
	public void onError(Throwable exception) {
		subscriptions.stream().map(SingleThreaded::getDelegate).forEach(t -> t.onError(exception));
	}

	@Override
	public void onNext(T value) {
		alreadyRequested.decrementAndGet();
		subscriptions.stream().map(SingleThreaded::getDelegate).forEach(t -> t.onNext(value));
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		Require.that(subscription == NONE);
		this.subscription = Require.nonNull(subscription);
	}

	public void registerCallback(LongFunction<CompletionStage<List<T>>> reader, AutoCloseable closer) {
		onSubscribe(new Subscription() {

			@Override
			public void cancel() {
				try {
					closer.close();
				} catch (Exception e) {
					Throwing.unchecked(e);
				}
			}

			@Override
			public void request(long n) {
				reader.apply(n).whenComplete(this::handle);
			}

			private void handle(List<T> results, Throwable exception) {
				if (results != null) {
					if (results.isEmpty()) {
						// TODO
					} else {
						results.forEach(Broadcast.this::onNext);
					}
				} else if (exception != null) {
					onError(exception);
				}
			}
		});
	}

	@Override
	public void subscribe(Subscriber<? super T> subscriber) {
		Target target = new Target(subscriber, alreadyRequested.get());
		subscriber.onSubscribe(target);
		subscriptions.add(new SingleThreaded<>(target));
	}
}
