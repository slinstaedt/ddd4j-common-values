package org.ddd4j.eventstore;

import org.ddd4j.infrastructure.channel.ColdSink;
import org.ddd4j.infrastructure.channel.ColdSource;
import org.ddd4j.infrastructure.channel.HotSink;
import org.ddd4j.infrastructure.channel.HotSource;

public class SchemaEventStore implements EventStore {

	interface SchemaHandler {

		<K, V> Committer<K, V> committer(EventChannel<K, V> channel, SinkCommitter committer);

		<K, V> Publisher<K, V> publisher(EventChannel<K, V> channel, SourcePublisher publisher);
	}

	private SchemaHandler handler;
	private ColdSink coldSink;
	private HotSink hotSink;
	private ColdSource coldSource;
	private HotSource hotSource;

	@Override
	public <K, V> Committer<K, V> committer(EventChannel<K, V> channel) {
		return handler.committer(channel, new SinkCommitter(channel.topic(), coldSink, hotSink));
	}

	@Override
	public <K, V> Publisher<K, V> publisher(EventChannel<K, V> channel) {
		return handler.publisher(channel, new SourcePublisher(channel.topic(), coldSource, hotSource));
	}
}
