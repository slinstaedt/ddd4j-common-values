package org.ddd4j.eventstore;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdSink;
import org.ddd4j.infrastructure.channel.ColdSource;
import org.ddd4j.infrastructure.channel.HotSink;
import org.ddd4j.infrastructure.channel.HotSource;

public class PubSubFactory {

	private final ColdSink coldSink;
	private final HotSink hotSink;
	private final ColdSource coldSource;
	private final HotSource hotSource;

	public PubSubFactory(ColdSink coldSink, HotSink hotSink, ColdSource coldSource, HotSource hotSource) {
		this.coldSink = Require.nonNull(coldSink);
		this.hotSink = Require.nonNull(hotSink);
		this.coldSource = Require.nonNull(coldSource);
		this.hotSource = Require.nonNull(hotSource);
	}

	public SinkCommitter createCommitter(ResourceDescriptor topic) {
		return new SinkCommitter(topic, coldSink, hotSink);
	}

	public SourcePublisher createPublisher(ResourceDescriptor topic) {
		return new SourcePublisher(topic, coldSource, hotSource);
	}
}
