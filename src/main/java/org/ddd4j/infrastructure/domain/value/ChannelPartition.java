package org.ddd4j.infrastructure.domain.value;

import java.util.function.BiFunction;

import org.ddd4j.util.Require;
import org.ddd4j.util.value.Value;

public class ChannelPartition implements Value<ChannelPartition> {

	private final ChannelName name;
	private final int partition;

	public ChannelPartition(ChannelName name, int partition) {
		this.name = Require.nonNull(name);
		this.partition = Require.that(partition, partition >= 0);
	}

	public ChannelPartition(String resource, int partition) {
		this(ChannelName.of(resource), partition);
	}

	public <E> E to(BiFunction<? super String, ? super Integer, E> mapper) {
		return mapper.apply(name.value(), partition);
	}

	public int getPartition() {
		return partition;
	}

	public ChannelName getName() {
		return name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + partition;
		result = prime * result + name.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		} else if (obj == null) {
			return false;
		} else if (getClass() != obj.getClass()) {
			return false;
		}
		ChannelPartition other = (ChannelPartition) obj;
		return partition == other.partition && name.equals(other.name);
	}
}
