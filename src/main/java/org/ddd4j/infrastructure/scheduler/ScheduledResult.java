package org.ddd4j.infrastructure.scheduler;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Result;
import org.ddd4j.value.Type;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class ScheduledResult<T> implements Result<T> {

	private class ScheduledSubscriber implements Subscriber<T> {

		private final Agent<Subscriber<? super T>> delegate;

		public ScheduledSubscriber(Subscriber<? super T> delegate) {
			this.delegate = scheduler.createAgent(delegate);
		}

		@Override
		public void onComplete() {
			delegate.perform(Subscriber::onComplete);
		}

		@Override
		public void onError(Throwable exception) {
			delegate.perform(s -> s.onError(exception));
		}

		@Override
		public void onNext(T value) {
			delegate.perform(s -> s.onNext(value));
		}

		@Override
		public void onSubscribe(Subscription subscription) {
			delegate.perform(s -> scheduler.createAgentDecorator(SUBSCRIPTION_TYPE, subscription));
		}
	}

	private static final Type<Subscription> SUBSCRIPTION_TYPE = Type.of(Subscription.class);

	private final Scheduler scheduler;
	private final Publisher<T> delegate;

	public ScheduledResult(Scheduler scheduler, Publisher<T> delegate) {
		this.scheduler = Require.nonNull(scheduler);
		this.delegate = Require.nonNull(delegate);
	}

	@Override
	public void subscribe(Subscriber<? super T> subscriber) {
		delegate.subscribe(new ScheduledSubscriber(subscriber));
	}
}
