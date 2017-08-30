package org.ddd4j.infrastructure.channel;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.Throwing;
import org.ddd4j.Throwing.TFunction;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.domain.ChannelSpec;
import org.ddd4j.infrastructure.channel.util.SchemaCodec;
import org.ddd4j.infrastructure.channel.util.SchemaCodec.Encoder;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Uncommitted;

public interface Committer<K, V> {

	interface Factory extends DataAccessFactory {

		Committer<ReadBuffer, ReadBuffer> createCommitter(ChannelName name);

		default <K, V> Committer<K, V> createCommitter(ChannelSpec<K, V> spec, SchemaCodec.Factory codecFactory,
				WriteBuffer.Pool bufferPool) {
			Encoder<V> encoder = codecFactory.encoder(spec);
			TFunction<CommitResult<?, ?>, Revision> committedRevision = r -> r.foldResult(Committed::getActual,
					c -> Throwing.unchecked(new IllegalStateException(c.toString())));
			return createCommitter(spec.getName()).onCompleted(ReadBuffer::close, ReadBuffer::close).flatMapValue(
					k -> bufferPool.get().accept(b -> spec.serializeKey(k, b)).flip(),
					(v, p) -> encoder.encode(bufferPool.get(), p.thenApply(committedRevision), v).thenApply(WriteBuffer::flip));
		}
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<? extends CommitResult<K, V>> commit(Uncommitted<K, V> attempt);

	default <X, Y> Committer<X, Y> flatMapValue(Function<? super X, K> key,
			BiFunction<? super Y, Promise<CommitResult<K, V>>, Promise<V>> value) {
		Promise.Deferred<CommitResult<K, V>> result = Promise.deferred(Runnable::run);
		return a -> value.apply(a.getValue(), result)
				.thenApply(v -> a.with(key, v))
				.thenCompose(this::commit)
				.whenComplete(result::complete)
				.thenApply(r -> r.withValuesFrom(a));
	}

	default <X, Y> Committer<X, Y> map(Function<? super X, K> key, Function<? super Y, V> value) {
		return a -> commit(a.map(key, value)).thenApply(r -> r.withValuesFrom(a));
	}

	default Committer<K, V> onCompleted(Consumer<? super K> key, Consumer<? super V> value) {
		return a -> commit(a).thenRun(() -> {
			key.accept(a.getKey());
			value.accept(a.getValue());
		});
	}
}