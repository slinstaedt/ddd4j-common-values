package org.ddd4j.infrastructure.channel.spi;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Ref;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Uncommitted;

public interface Committer<K, V> {

	interface Factory extends DataAccessFactory {

		Committer<ReadBuffer, ReadBuffer> createCommitter(ChannelName name);

		default Committer<ReadBuffer, ReadBuffer> createCommitterClosingBuffers(ChannelName name) {
			return createCommitter(name).onCompleted(ReadBuffer::close, ReadBuffer::close);
		}
	}

	Ref<Factory> FACTORY = Ref.of(Factory.class);

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