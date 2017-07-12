package org.ddd4j.infrastructure.channel;

import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revisions;
import org.ddd4j.value.versioned.Uncommitted;

public interface Writer<K, V> extends Committer<K, V> {

	interface Factory extends DataAccessFactory {

		Writer<ReadBuffer, ReadBuffer> create(ResourceDescriptor resource);

		default Writer<ReadBuffer, ReadBuffer> createClosingBuffers(ResourceDescriptor resource) {
			return create(resource).whenComplete(ReadBuffer::close, ReadBuffer::close);
		}
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	@Override
	default Promise<? extends CommitResult<K, V>> commit(Uncommitted<K, V> attempt) {
		return put(attempt);
	}

	@Override
	default <X, Y> Writer<X, Y> map(Function<? super X, K> key, Function<? super Y, V> value) {
		return r -> put(r.map(key, value)).thenApply(c -> c.with(r.getKey(), r.getValue()));
	}

	default Promise<Committed<K, V>> put(K key, V value) {
		return put(Recorded.uncommitted(key, value, Revisions.NONE));
	}

	Promise<Committed<K, V>> put(Recorded<K, V> attempt);

	@Override
	default Writer<K, V> whenComplete(Consumer<? super K> key, Consumer<? super V> value) {
		return a -> put(a).thenRun(() -> {
			key.accept(a.getKey());
			value.accept(a.getValue());
		});
	}
}