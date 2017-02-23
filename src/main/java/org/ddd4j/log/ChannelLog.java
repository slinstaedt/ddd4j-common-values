package org.ddd4j.log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.Channel;
import org.ddd4j.infrastructure.channel.ColdChannel;
import org.ddd4j.infrastructure.channel.HotChannel;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public class ChannelLog {

	private class ColdListener implements ColdChannel.Listener {

		@Override
		public void onError(Throwable throwable) {
			subscriptions.values().forEach(s -> s.onError(throwable));
		}

		@Override
		public void onNext(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
			subscriptions.get(topic).onNextCold(committed);
		}
	}

	private class HotListener implements HotChannel.Listener {

		@Override
		public void onPartitionsAssigned(ResourceDescriptor topic, int[] partitions) {
			subscriptions.get(topic).loadRevisions(partitions);
		}

		@Override
		public void onPartitionsRevoked(ResourceDescriptor topic, int[] partitions) {
			subscriptions.get(topic).saveRevisions(partitions);
		}

		@Override
		public void onError(Throwable throwable) {
			subscriptions.values().forEach(s -> s.onError(throwable));
		}

		@Override
		public void onNext(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
			subscriptions.get(topic).onNextHot(committed);
		}
	}

	private final Channel channel;
	private final Map<ResourceDescriptor, ChannelPublisher> subscriptions;
	private final Channel.Callback callback;

	public ChannelLog(Channel channel) {
		this.channel = Require.nonNull(channel);
		this.subscriptions = new ConcurrentHashMap<>();
		this.callback = channel.register(new ColdListener(), new HotListener());
	}

	public ChannelCommitter committer(ResourceDescriptor topic) {
		return new ChannelCommitter(topic, channel);
	}

	public ChannelPublisher publisher(ResourceDescriptor topic) {
		return subscriptions.computeIfAbsent(topic, t -> new ChannelPublisher(t, callback));
	}

	void unsubscribe(ResourceDescriptor topic) {
		subscriptions.remove(topic);
	}
}
