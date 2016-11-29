package org.ddd4j.infrastructure.scheduler;

import org.ddd4j.contract.Require;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

public class ScheduledPublisher<T> implements Publisher<T> {

	private final Agent<Publisher<T>> delegate;

	public ScheduledPublisher(Agent<Publisher<T>> delegate) {
		this.delegate = Require.nonNull(delegate);
	}

	@Override
	public void subscribe(Subscriber<? super T> subscriber) {
		delegate.perform(p -> p.subscribe(subscriber));
	}
}