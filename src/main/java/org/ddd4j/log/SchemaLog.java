package org.ddd4j.log;

import org.ddd4j.io.ReadBuffer;

public class SchemaLog implements Log {

	public interface SchemaHandler {

		<K, V> Committer<K, V> committer(LogChannel<K, V> channel, Committer<ReadBuffer, ReadBuffer> committer);

		<K, V> Publisher<K, V> publisher(LogChannel<K, V> channel, Publisher<ReadBuffer, ReadBuffer> publisher);
	}

	private ChannelLog delegate;
	private SchemaHandler handler;

	@Override
	public <K, V> Committer<K, V> committer(LogChannel<K, V> channel) {
		return handler.committer(channel, delegate.committer(channel.topic()));
	}

	@Override
	public <K, V> Publisher<K, V> publisher(LogChannel<K, V> channel) {
		return handler.publisher(channel, delegate.publisher(channel.topic()));
	}
}
