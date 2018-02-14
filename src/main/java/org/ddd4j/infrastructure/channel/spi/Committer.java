package org.ddd4j.infrastructure.channel.spi;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.Throwing;
import org.ddd4j.Throwing.TFunction;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.SchemaCodec;
import org.ddd4j.infrastructure.channel.SchemaCodec.Encoder;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelSpec;
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
			return createCommitterClosingBuffers(spec.getName()).flatMapValue(
					k -> bufferPool.get().accept(b -> spec.serializeKey(k, b)).flip(),
					(v, p) -> encoder.encode(bufferPool.get(), p.thenApply(committedRevision), v).thenApply(WriteBuffer::flip));
		}

		default Committer<ReadBuffer, ReadBuffer> createCommitterClosingBuffers(ChannelName name) {
			return createCommitter(name).onCompleted(ReadBuffer::close, ReadBuffer::close);
		}

		default Factory publishCommittedTo(Writer.Factory factory) {
			return name -> {
				Committer<ReadBuffer, ReadBuffer> committer = createCommitter(name);
				Writer<ReadBuffer, ReadBuffer> writer = factory.createWriter(name);
				return attempt -> committer.commit(withBuffers(attempt, ReadBuffer::mark))
						.thenRun(() -> withBuffers(attempt, ReadBuffer::reset))
						// compiler's type inference failed
						.thenCompose(r -> r.<Promise<? extends CommitResult<ReadBuffer, ReadBuffer>>>onCommitted(writer::put,
								Promise.completed(r)))
						.thenRun(() -> withBuffers(attempt, ReadBuffer::reset));
			};
		}
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<? extends CommitResult<K, V>> commit(Uncommitted<K, V> attempt);

	default <X, Y> Committer<X, Y> flatMapValue(Function<? super X, K> key,
			BiFunction<? super Y, Promise<CommitResult<K, V>>, Promise<V>> value) {
		Promise.Deferred<CommitResult<K, V>> result = Promise.deferred(Runnable::run);
		return attempt -> value.apply(attempt.getValue(), result)
				.thenApply(v -> attempt.mapKey(key, v))
				.thenCompose(this::commit)
				.whenComplete(result::complete)
				.thenApply(r -> r.withKeyValueFrom(attempt));
	}

	default <X, Y> Committer<X, Y> map(Function<? super X, K> key, Function<? super Y, V> value) {
		return attempt -> commit(attempt.map(key, value)).thenApply(r -> r.withKeyValueFrom(attempt));
	}

	default Committer<K, V> onCompleted(Consumer<? super K> key, Consumer<? super V> value) {
		return attempt -> commit(attempt).thenRun(() -> {
			key.accept(attempt.getKey());
			value.accept(attempt.getValue());
		});
	}
}