package org.ddd4j.infrastructure.channel.old;

import org.ddd4j.infrastructure.ResourceDescriptor;

public interface PartitionRebalanceListener {

	PartitionRebalanceListener NOOP_LISTENER = new PartitionRebalanceListener() {

		@Override
		public void onPartitionsRevoked(ResourceDescriptor topic, int[] partitions) {
			// ignore
		}

		@Override
		public void onPartitionsAssigned(ResourceDescriptor topic, int[] partitions) {
			// ignore
		}
	};

	void onPartitionsAssigned(ResourceDescriptor topic, int[] partitions);

	void onPartitionsRevoked(ResourceDescriptor topic, int[] partitions);
}
