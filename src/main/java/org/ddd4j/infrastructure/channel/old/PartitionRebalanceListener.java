package org.ddd4j.infrastructure.channel.old;

import org.ddd4j.infrastructure.ChannelName;

public interface PartitionRebalanceListener {

	PartitionRebalanceListener NOOP_LISTENER = new PartitionRebalanceListener() {

		@Override
		public void onPartitionsRevoked(ChannelName topic, int[] partitions) {
			// ignore
		}

		@Override
		public void onPartitionsAssigned(ChannelName topic, int[] partitions) {
			// ignore
		}
	};

	void onPartitionsAssigned(ChannelName topic, int[] partitions);

	void onPartitionsRevoked(ChannelName topic, int[] partitions);
}
