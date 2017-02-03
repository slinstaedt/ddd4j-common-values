package org.ddd4j.eventstore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.ddd4j.contract.Require;
import org.ddd4j.eventstore.EventStore.Publisher;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ChannelListener;
import org.ddd4j.infrastructure.channel.RevisionsCallback;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

// TODO delegate to subscriber in a async way?
public class ChannelPublisher implements Publisher<ReadBuffer, ReadBuffer> {

	private static class Listener implements ChannelListener {

		private ColdChannelCallback coldCallback;
		private HotChannelCallback hotCallback;

		public Listener() {
			// TODO Auto-generated constructor stub
		}

		@Override
		public void onError(Throwable throwable) {
			// TODO Auto-generated method stub
		}

		@Override
		public void onPartitionsAssigned(ResourceDescriptor topic, IntStream partitions) {
			// TODO Auto-generated method stub
		}

		@Override
		public void onPartitionsRevoked(ResourceDescriptor topic, IntStream partitions) {
			// TODO Auto-generated method stub
		}

		@Override
		public void onNext(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
		}

		@Override
		public void onColdRegistration(ColdChannelCallback callback) {
			coldCallback = Require.nonNull(callback);
		}

		@Override
		public void onHotRegistration(HotChannelCallback callback) {
			hotCallback = Require.nonNull(callback);
		}
	}

	private class ChannelSubscription implements Subscription {

		private final Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber;
		private final RevisionsCallback callback;

		ChannelSubscription(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback) {
			this.subscriber = Require.nonNull(subscriber);
			this.callback = Require.nonNull(callback);
			subscriber.onSubscribe(this);
		}

		@Override
		public void request(long n) {
			// TODO Auto-generated method stub
		}

		@Override
		public void cancel() {
			// TODO Auto-generated method stub
		}
	}

	private final ResourceDescriptor topic;
	private final Map<Subscriber<Committed<ReadBuffer, ReadBuffer>>, ChannelSubscription> subscriptions;

	public ChannelPublisher(ResourceDescriptor topic) {
		this.topic = Require.nonNull(topic);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	@Override
	public void subscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback) {
		subscriptions.computeIfAbsent(subscriber, s -> new ChannelSubscription(s, callback));
	}
}
