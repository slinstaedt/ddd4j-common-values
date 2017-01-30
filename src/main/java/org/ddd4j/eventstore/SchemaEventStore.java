package org.ddd4j.eventstore;

public class SchemaEventStore implements EventStore {

	public interface SchemaHandler {

		<K, V> Committer<K, V> committer(EventChannel<K, V> channel, SinkCommitter committer);

		<K, V> Publisher<K, V> publisher(EventChannel<K, V> channel, SourcePublisher publisher);
	}

	private PubSubFactory factory;
	private SchemaHandler handler;

	@Override
	public <K, V> Committer<K, V> committer(EventChannel<K, V> channel) {
		return handler.committer(channel, factory.createCommitter(channel.topic()));
	}

	@Override
	public <K, V> Publisher<K, V> publisher(EventChannel<K, V> channel) {
		return handler.publisher(channel, factory.createPublisher(channel.topic()));
	}
}
