package org.ddd4j.eventstore;

import org.ddd4j.contract.Require;
import org.ddd4j.eventstore.Repository.Publisher;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdSource;
import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.infrastructure.channel.RevisionsCallback;
import org.ddd4j.infrastructure.channel.SourceSubscriber;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.versioned.Committed;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class SourcePublisher implements Publisher<ReadBuffer, ReadBuffer> {

	// TODO delegate to subscriber in a async way?
	private class SourcedSubscriber implements SourceSubscriber, Subscription {

		private final ResourceDescriptor channel;
		private final Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber;
		private final RevisionsCallback callback;
		private final Requesting requesting;
		private SourceSubscription subscription;
		private boolean latest;

		SourcedSubscriber(ResourceDescriptor channel, Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback) {
			this.channel = Require.nonNull(channel);
			this.subscriber = Require.nonNull(subscriber);
			this.callback = Require.nonNull(callback);
			this.requesting = new Requesting();
			this.latest = false;
		}

		@Override
		public void cancel() {
			Require.nonNull(subscription).cancel();
		}

		@Override
		public void onComplete() {
			try {
				if (latest) {
					coldSource.read(channel, this, callback);
				} else {
					hotSource.subscribe(channel, this, callback);
				}
				latest = !latest;
			} catch (Exception e) {
				Throwing.unchecked(e);
			}
		}

		@Override
		public void onError(Throwable throwable) {
			subscriber.onError(throwable);
		}

		@Override
		public void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
			if (requesting.hasRemaining()) {
				requesting.processed();
				subscriber.onNext(committed);
			} else {
				latest = true;
				onComplete();
			}
		}

		@Override
		public void onSubscribe(SourceSubscription subscription) {
			this.subscription = Require.nonNull(subscription);
		}

		@Override
		public void request(long n) {
			requesting.more(n);
		}
	}

	private final ResourceDescriptor topic;
	private final ColdSource coldSource;
	private final HotSource hotSource;

	public SourcePublisher(ResourceDescriptor topic, ColdSource coldSource, HotSource hotSource) {
		this.topic = Require.nonNull(topic);
		this.coldSource = Require.nonNull(coldSource);
		this.hotSource = Require.nonNull(hotSource);
	}

	@Override
	public void subscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback) {
		new SourcedSubscriber(topic, subscriber, callback).onComplete();
	}
}
