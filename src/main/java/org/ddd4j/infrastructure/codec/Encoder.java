package org.ddd4j.infrastructure.codec;

import java.util.function.BiConsumer;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.value.versioned.Revision;

@FunctionalInterface
public interface Encoder<T> {

	static <T> Encoder<T> from(BiConsumer<? super T, WriteBuffer> serializer) {
		Require.nonNull(serializer);
		return (buf, rev, val) -> {
			serializer.accept(val, buf);
			return Promise.completed(buf);
		};
	}

	Promise<WriteBuffer> encode(WriteBuffer buffer, Promise<Revision> revision, T value);
}