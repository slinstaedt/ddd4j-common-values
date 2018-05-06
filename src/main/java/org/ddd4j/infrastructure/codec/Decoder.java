package org.ddd4j.infrastructure.codec;

import java.util.function.Function;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.value.versioned.Revision;

@FunctionalInterface
public interface Decoder<T> {

	static <T> Decoder<T> from(Function<ReadBuffer, ? extends T> deserializer) {
		Require.nonNull(deserializer);
		return (buf, rev) -> Promise.completed(deserializer.apply(buf));
	}

	Promise<T> decode(ReadBuffer buffer, Revision revision);
}