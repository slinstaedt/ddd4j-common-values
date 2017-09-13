package org.ddd4j.infrastructure.channel.api;

import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.channel.domain.ChannelPartition;

public interface RepartitioningListener {

	RepartitioningListener VOID = new RepartitioningListener() {

		@Override
		public void onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
			// ignore
		}

		@Override
		public void onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
			// ignore
		}
	};

	void onPartitionsAssigned(Sequence<ChannelPartition> partitions);

	void onPartitionsRevoked(Sequence<ChannelPartition> partitions);
}
