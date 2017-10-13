package org.ddd4j.infrastructure.channel.api;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.util.Sequence;

public interface RepartitioningListener {

	RepartitioningListener VOID = new RepartitioningListener() {

		@Override
		public Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
			return Promise.completed();
		}

		@Override
		public Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
			return Promise.completed();
		}
	};

	Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions);

	Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions);
}
