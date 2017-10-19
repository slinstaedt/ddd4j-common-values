package org.ddd4j.infrastructure.domain.value;

import java.util.function.BiFunction;

import org.ddd4j.Require;
import org.ddd4j.value.Value;
import org.ddd4j.value.versioned.Revision;

public class ChannelRevision extends Value.Comlex<ChannelRevision> {

	private final ChannelName name;
	private final Revision revision;

	public ChannelRevision(ChannelName name, int partition, long offset) {
		this(name, new Revision(partition, offset));
	}

	public ChannelRevision(ChannelName name, Revision revision) {
		this.name = Require.nonNull(name);
		this.revision = Require.nonNull(revision);
	}

	public ChannelRevision(ChannelPartition partition, long offset) {
		this(partition.getName(), partition.getPartition(), offset);
	}

	public ChannelRevision(ChannelPartition partition, Revision revision) {
		this(partition.getName(), Require.that(revision, partition.getPartition() == revision.getOffset()));
	}

	public ChannelRevision(String channelName, int partition, long offset) {
		this(ChannelName.of(channelName), new Revision(partition, offset));
	}

	public ChannelName getName() {
		return name;
	}

	public String getNameAsString() {
		return name.value();
	}

	public long getOffset() {
		return revision.getOffset();
	}

	public ChannelPartition getPartition() {
		return new ChannelPartition(name, revision.getPartition());
	}

	public int getPartitionAsInteger() {
		return revision.getPartition();
	}

	public Revision getRevision() {
		return revision;
	}

	public <E> E to(BiFunction<? super String, ? super Integer, E> mapper) {
		return mapper.apply(name.value(), revision.getPartition());
	}

	@Override
	protected Value<?>[] value() {
		return new Value<?>[] { name, revision };
	}
}
