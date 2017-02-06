package org.ddd4j.eventstore;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ChannelListener;
import org.ddd4j.infrastructure.channel.ColdChannel;
import org.ddd4j.infrastructure.channel.HotChannel;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;
import org.reactivestreams.Subscriber;

//TODO delegate to subscriber in a async way?
public class ChannelRepository implements ChannelListener {

	private final ColdChannel coldChannel;
	private final HotChannel hotChannel;
	private final ConcurrentMap<ResourceDescriptor, ChannelPublisher> subscriptions;

	private ColdChannelCallback coldCallback;
	private HotChannelCallback hotCallback;

	public ChannelRepository(ColdChannel coldChannel, HotChannel hotChannel) {
		this.coldChannel = Require.nonNull(coldChannel);
		this.hotChannel = Require.nonNull(hotChannel);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	public ChannelCommitter committer(ResourceDescriptor topic) {
		return new ChannelCommitter(topic, coldChannel, hotChannel);
	}

	public ChannelPublisher publisher(ResourceDescriptor topic) {
		return subscriptions.computeIfAbsent(topic, ChannelPublisher::new);
	}

	void unsubscribe(ResourceDescriptor topic, Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber) {
		subscriptions.computeIfPresent(topic, (t, s) -> {
			if (s.unsubscribe(subscriber)) {
				client.perform(c -> c.subscribe(subscriptions.keySet(), this));
				return null;
			} else {
				return s;
			}
		});
	}

	@Override
	public void onError(Throwable throwable) {
		subscriptions.values().forEach(s -> s.onError(throwable));
	}

	@Override
	public void onPartitionsAssigned(ResourceDescriptor topic, IntStream partitions) {
		subscriptions.get(topic).loadRevisions(partitions);
	}

	@Override
	public void onPartitionsRevoked(ResourceDescriptor topic, IntStream partitions) {
		subscriptions.get(topic).saveRevisions(partitions);
	}

	@Override
	public void onNext(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
		subscriptions.get(topic).onNext(committed);
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
