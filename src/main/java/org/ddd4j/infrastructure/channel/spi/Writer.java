package org.ddd4j.infrastructure.channel.spi;

import java.time.Instant;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.SchemaCodec;
import org.ddd4j.infrastructure.channel.SchemaCodec.Encoder;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelSpec;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Committed.Published;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revisions;

public interface Writer<K, V> {

	interface Factory extends DataAccessFactory {

		Writer<ReadBuffer, ReadBuffer> createWriter(ChannelName name);

		default <K, V> Writer<K, V> createWriter(ChannelSpec<K, V> spec, SchemaCodec.Factory codecFactory, WriteBuffer.Pool bufferPool) {
			Encoder<V> encoder = codecFactory.encoder(spec);
			return createWriterClosingBuffers(spec.getName()).flatMapValue(
					k -> bufferPool.get().accept(b -> spec.serializeKey(k, b)).flip(),
					(v, p) -> encoder.encode(bufferPool.get(), p.thenApply(Committed::getActual), v).thenApply(WriteBuffer::flip));
		}

		default Writer<ReadBuffer, ReadBuffer> createWriterClosingBuffers(ChannelName name) {
			return createWriter(name).onCompleted(ReadBuffer::close, ReadBuffer::close);
		}
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	default <X, Y> Writer<X, Y> flatMapValue(Function<? super X, K> key,
			BiFunction<? super Y, Promise<Committed<K, V>>, Promise<V>> value) {
		Promise.Deferred<Committed<K, V>> result = Promise.deferred(Runnable::run);
		return recorded -> value.apply(recorded.getValue(), result)
				.thenApply(v -> recorded.mapKey(key, v))
				.thenCompose(this::put)
				.whenComplete(result::complete)
				.thenApply(p -> p.withKeyValueFrom(recorded));
	}

	default <X, Y> Writer<X, Y> map(Function<? super X, K> key, Function<? super Y, V> value) {
		return recorded -> put(recorded.map(key, value)).thenApply(p -> p.withKeyValueFrom(recorded));
	}

	default Writer<K, V> onCompleted(Consumer<? super K> key, Consumer<? super V> value) {
		return recorded -> put(recorded).thenRun(() -> {
			key.accept(recorded.getKey());
			value.accept(recorded.getValue());
		});
	}

	default Promise<Published<K, V>> put(K key, V value) {
		return put(Recorded.uncommitted(key, value, Revisions.NONE, Instant.now()));
	}

	Promise<Published<K, V>> put(Recorded<K, V> recorded);
}