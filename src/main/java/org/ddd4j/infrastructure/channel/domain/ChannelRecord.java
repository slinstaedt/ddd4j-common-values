package org.ddd4j.infrastructure.channel.domain;

import java.util.function.BiConsumer;

import org.ddd4j.Require;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public class ChannelRecord {

	private final ChannelName name;
	private final Committed<ReadBuffer, ReadBuffer> committed;

	public ChannelRecord(String name, Committed<ReadBuffer, ReadBuffer> committed) {
		this(ChannelName.of(name), committed);
	}

	public ChannelRecord(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
		this.name = Require.nonNull(name);
		this.committed = Require.nonNull(committed);
	}

	public void accept(BiConsumer<? super ChannelName, ? super Committed<ReadBuffer, ReadBuffer>> consumer) {
		consumer.accept(name, committed);
	}

	public ChannelRevision getRevision() {
		return new ChannelRevision(name, committed.getActual());
	}

	public ChannelName getName() {
		return name;
	}

	public Committed<ReadBuffer, ReadBuffer> getCommitted() {
		return committed;
	}
}
