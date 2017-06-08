package org.ddd4j.repository;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.log.RevisionsCallback;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Uncommitted;
import org.reactivestreams.Subscriber;

public interface Repository<K, V> {

	interface Definition<K, V> {

		ResourceDescriptor getDescriptor();

		void serializeKey(WriteBuffer buffer, K key);
	}

	interface ChannelPublisher<K, V> {

		interface Factory {

			Key<Factory> KEY = Key.of(Factory.class);

			<K, V> ChannelPublisher<K, V> publisher(Definition<K, V> definition);
		}

		void subscribe(Subscriber<Committed<K, V>> subscriber, RevisionsCallback callback);
	}

	interface ChannelReader<K, V> {

		interface Factory {

			Key<Factory> KEY = Key.of(Factory.class);

			<K, V> ChannelReader<K, V> reader(Definition<K, V> definition);
		}

		Promise<Committed<K, V>> get(K key);
	}

	interface ChannelWriter<K, V> {

		interface Factory {

			Key<Factory> KEY = Key.of(Factory.class);

			<K, V> ChannelWriter<K, V> writer(Definition<K, V> definition);
		}

		Promise<CommitResult<K, V>> put(Uncommitted<K, V> attempt);
	}

	Promise<Committed<K, V>> get(K key);

	Promise<CommitResult<K, V>> put(Uncommitted<K, V> attempt);
}
