package org.ddd4j.infrastructure.codec;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.value.versioned.Revision;

@FunctionalInterface
public interface Encoder<T> {

	@FunctionalInterface
	interface Extender<S, T> {

		static <X> Extender<X, X> none() {
			return (val, buf) -> val;
		}

		T unwrap(S value, WriteBuffer buffer);
	}

	Promise<WriteBuffer> encode(WriteBuffer buffer, Promise<Revision> revision, T value);

	default <X> Encoder<X> extend(Extender<? super X, ? extends T> extender) {
		Require.nonNull(extender);
		return (buf, rev, val) -> encode(buf, rev, extender.unwrap(val, buf));
	}
}