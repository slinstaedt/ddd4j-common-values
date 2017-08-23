package org.ddd4j.infrastructure.channel.domain;

import org.ddd4j.Require;
import org.ddd4j.value.Value;
import org.ddd4j.value.versioned.Revision;

public class ChannelRevision extends Value.Comlex<ChannelRevision> {

	private final ChannelName name;
	private final Revision revision;

	public ChannelRevision(ChannelName name, Revision revision) {
		this.name = Require.nonNull(name);
		this.revision = Require.nonNull(revision);
	}

	public ChannelRevision(String resource, int partition, long offset) {
		this(ChannelName.of(resource), new Revision(partition, offset));
	}

	public long getOffset() {
		return revision.getOffset();
	}

	public int getPartition() {
		return revision.getPartition();
	}

	public ChannelName getName() {
		return name;
	}

	public Revision getRevision() {
		return revision;
	}

	@Override
	protected Value<?>[] value() {
		return new Value<?>[] { name, revision };
	}
}
