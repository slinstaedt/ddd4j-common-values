package org.ddd4j.infrastructure.channel.api;

import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.domain.ChannelPartition;

public interface RepartitioningListener {

	RepartitioningListener VOID = new RepartitioningListener() {

		@Override
		public Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
			return Promise.completed();
		}

		@Override
		public Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
			return Promise.completed();
		}
	};

	Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions);

	Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions);
}
