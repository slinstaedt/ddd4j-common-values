package org.ddd4j.infrastructure.channel;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.repository.RepositoryDefinition;
import org.ddd4j.repository.SchemaCodec;
import org.ddd4j.repository.SchemaCodec.Encoder;
import org.ddd4j.spi.Key;
import org.ddd4j.value.Value;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Uncommitted;

public interface Committer<K, V> {

	interface Factory extends DataAccessFactory {

		default <K extends Value<K>, V> Committer<K, V> create(RepositoryDefinition<K, V> definition, SchemaCodec.Factory codecFactory,
				Supplier<WriteBuffer> bufferSupplier) {
			Encoder<V> encoder = codecFactory.encoder(definition.getType().getRawType());
			return create(definition.getDescriptor()).whenComplete(ReadBuffer::close, ReadBuffer::close).flatMapValue(
					k -> bufferSupplier.get().accept(b -> definition.serializeKey(k, b)).flip(),
					v -> encoder.encode(bufferSupplier.get(), v).thenApply(WriteBuffer::flip));
		}

		Committer<ReadBuffer, ReadBuffer> create(ResourceDescriptor resource);
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<? extends CommitResult<K, V>> commit(Uncommitted<K, V> attempt);

	default <X, Y> Committer<X, Y> flatMapValue(Function<? super X, K> key, Function<? super Y, Promise<V>> value) {
		return a -> value.apply(a.getValue())
				.thenApply(v -> a.map(key, any -> v))
				.thenCompose(this::commit)
				.thenApply(r -> r.with(a.getKey(), a.getValue()));
	}

	default <X, Y> Committer<X, Y> map(Function<? super X, K> key, Function<? super Y, V> value) {
		return a -> commit(a.map(key, value)).thenApply(c -> c.with(a.getKey(), a.getValue()));
	}

	default Committer<K, V> whenComplete(Consumer<? super K> key, Consumer<? super V> value) {
		return a -> commit(a).thenRun(() -> {
			key.accept(a.getKey());
			value.accept(a.getValue());
		});
	}
}