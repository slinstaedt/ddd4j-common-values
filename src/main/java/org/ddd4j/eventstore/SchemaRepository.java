package org.ddd4j.eventstore;

public class SchemaRepository implements Repository {

	public interface SchemaHandler {

		<K, V> Committer<K, V> committer(EventChannel<K, V> channel, ChannelCommitter committer);

		<K, V> Publisher<K, V> publisher(EventChannel<K, V> channel, ChannelPublisher publisher);
	}

	private ChannelRepository repository;
	private SchemaHandler handler;

	@Override
	public <K, V> Committer<K, V> committer(EventChannel<K, V> channel) {
		return handler.committer(channel, repository.committer(channel.topic()));
	}

	@Override
	public <K, V> Publisher<K, V> publisher(EventChannel<K, V> channel) {
		return handler.publisher(channel, repository.publisher(channel.topic()));
	}
}
