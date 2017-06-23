package org.ddd4j.repository.api;

import java.util.function.Function;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Uncommitted;

public interface Committer<K, V> {

	interface Factory extends Throwing.Closeable {

		Key<Factory> KEY = Key.of(Factory.class);

		Committer<ReadBuffer, ReadBuffer> create(ResourceDescriptor descriptor);
	}

	Promise<? extends CommitResult<K, V>> commit(Uncommitted<K, V> attempt);

	default <X, Y> Committer<X, Y> map(Function<? super X, K> key, Function<? super Y, V> value) {
		return r -> commit(r.map(key, value)).thenApply(c -> c.with(r.getKey(), r.getValue()));
	}
}