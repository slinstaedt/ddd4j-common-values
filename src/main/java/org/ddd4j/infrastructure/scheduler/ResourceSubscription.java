package org.ddd4j.infrastructure.scheduler;

import java.util.Collection;

import org.ddd4j.contract.Require;
import org.reactivestreams.Subscriber;

public class ResourceSubscription<T> implements RequestingSubscription {

	private static int toInt(long n) {
		return (int) Long.min(n, Integer.MAX_VALUE);
	}

	private final Subscriber<? super T> subscriber;
	private final Resource<? extends T> resource;

	private long requested;

	public ResourceSubscription(Subscriber<? super T> subscriber, Resource<? extends T> resource) {
		this.subscriber = Require.nonNull(subscriber);
		this.resource = Require.nonNull(resource);
		this.requested = 0;
	}

	@Override
	public void cancel() {
		if (requested >= 0) {
			requested = -1;
			resource.close();
		}
	}

	private void handle(Collection<? extends T> results, Throwable exception) {
		if (exception != null) {
			try {
				subscriber.onError(exception);
			} finally {
				cancel();
			}
		} else if (results.isEmpty()) {
			try {
				subscriber.onComplete();
			} finally {
				cancel();
			}
		} else {
			requested -= results.size();
			results.forEach(subscriber::onNext);
		}
	}

	@Override
	public void request(long n) {
		requested += requesting(requested, n);
		while (requested > 0) {
			try {
				handle(resource.request(toInt(requested)), null);
			} catch (Exception e) {
				handle(null, e);
			}
		}
	}
}
