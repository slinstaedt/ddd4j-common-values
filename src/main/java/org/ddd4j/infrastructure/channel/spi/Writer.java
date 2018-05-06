package org.ddd4j.infrastructure.channel.spi;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.header.Headers;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Ref;
import org.ddd4j.value.versioned.Committed.Published;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;

public interface Writer<K, V> {

	interface Factory extends DataAccessFactory {

		Writer<ReadBuffer, ReadBuffer> createWriter(ChannelName name);

		default Writer<ReadBuffer, ReadBuffer> createWriterClosingBuffers(ChannelName name) {
			return createWriter(name).onCompleted(ReadBuffer::close, ReadBuffer::close);
		}
	}

	Ref<Factory> FACTORY = Ref.of(Factory.class);

	default <X, Y> Writer<X, Y> map(Function<? super X, K> key, Function<? super Y, V> value) {
		return recorded -> put(recorded.map(key, value)).thenApply(p -> p.withKeyValueFrom(recorded));
	}

	default <X, Y> Writer<X, Y> mapPromised(Promise.Deferred<Revision> revision, Function<? super X, Promise<K>> key,
			Function<? super Y, Promise<V>> value) {
		return recorded -> key.apply(recorded.getKey())
				.thenCombine(value.apply(recorded.getValue()), recorded::withKeyValue)
				.thenCompose(this::put)
				.thenApply(r -> r.withKeyValueFrom(recorded))
				.whenCompleteSuccessfully(r -> revision.completeSuccessfully(r.getActual()))
				.whenCompleteExceptionally(revision::completeExceptionally);
	}

	default Writer<K, V> onCompleted(Consumer<? super K> key, Consumer<? super V> value) {
		return recorded -> put(recorded).thenRun(() -> {
			key.accept(recorded.getKey());
			value.accept(recorded.getValue());
		});
	}

	default Promise<Published<K, V>> put(K key, V value) {
		return put(Recorded.uncommitted(key, value, Headers.EMPTY, Instant.now(), Revisions.NONE));
	}

	Promise<Published<K, V>> put(Recorded<K, V> recorded);
}