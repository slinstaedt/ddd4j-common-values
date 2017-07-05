package org.ddd4j.log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.old.Channel;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.log.Log.Committer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;

public class ChannelLog {

	private class Listener implements Channel.Listener {

		@Override
		public void onError(Throwable throwable) {
			subscriptions.values().forEach(s -> s.onError(throwable));
			subscriptions.clear();
			channel.close();
		}

		@Override
		public void onNext(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
			subscriptions.getOrDefault(topic, ChannelPublisher.VOID).onNext(committed);
		}

		@Override
		public void onPartitionsAssigned(ResourceDescriptor topic, int[] partitions) {
			subscriptions.getOrDefault(topic, ChannelPublisher.VOID).loadRevisions(partitions);
		}

		@Override
		public void onPartitionsRevoked(ResourceDescriptor topic, int[] partitions) {
			subscriptions.getOrDefault(topic, ChannelPublisher.VOID).saveRevisions(partitions);
		}
	}

	public static final Key<ChannelLog> KEY = Key.of(ChannelLog.class, ctx -> new ChannelLog(ctx.get(Channel.KEY)));

	private final Channel channel;
	private final Map<ResourceDescriptor, ChannelPublisher> subscriptions;
	private final Channel.Callback callback;

	public ChannelLog(Channel channel) {
		this.channel = Require.nonNull(channel);
		this.subscriptions = new ConcurrentHashMap<>();
		this.callback = channel.register(new Listener());
	}

	public Committer<ReadBuffer, ReadBuffer> committer(ResourceDescriptor topic) {
		Require.nonNull(topic);
		return attempt -> channel.trySend(topic, attempt);
	}

	public ChannelPublisher publisher(ResourceDescriptor topic) {
		return subscriptions.computeIfAbsent(topic, t -> new ChannelPublisher(t, callback));
	}
}
