package org.ddd4j.infrastructure.channel;

import java.util.Arrays;
import java.util.stream.IntStream;

public class PartitionAssignments {

	private final boolean[] partitions;

	public PartitionAssignments(int partitionSize) {
		this.partitions = new boolean[partitionSize];
		Arrays.fill(partitions, false);
	}

	public IntStream assigned() {
		return IntStream.range(0, partitions.length).filter(p -> partitions[p]);
	}

	public void assigned(int partition) {
		partitions[partition] = true;
	}

	public int partitionSize() {
		return partitions.length;
	}

	public void unassigned(int partition) {
		partitions[partition] = false;
	}
}