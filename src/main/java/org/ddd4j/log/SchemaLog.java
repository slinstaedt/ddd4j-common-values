package org.ddd4j.log;

public class SchemaLog implements Log {

	public interface SchemaHandler {

		<K, V> Committer<K, V> committer(LogChannel<K, V> channel, ChannelCommitter committer);

		<K, V> Publisher<K, V> publisher(LogChannel<K, V> channel, ChannelPublisher publisher);
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
