package org.ddd4j.io.codec;

import java.util.Optional;
import java.util.function.Consumer;

import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.util.value.Sequence;

@FunctionalInterface
public interface Encoding<T, R> {

	static <T> Encoding<T, T> writeNullable() {
		return Encoding.<T>writeOptional().<T>extend((val, buf, writer) -> writer.accept(Optional.ofNullable(val)));
	}

	static <T> Encoding<T, Optional<T>> writeOptional() {
		return (val, buf, writer) -> {
			buf.putBoolean(val.isPresent());
			val.ifPresent(writer);
		};
	}

	static <T> Encoding<T, Sequence<T>> writeSequence() {
		return (val, buf, writer) -> {
			buf.putUnsignedVarInt(val.size());
			val.forEach(writer);
		};
	}

	static <T> Encoding<T, T> writeValue() {
		return (val, buf, writer) -> writer.accept(Require.nonNull(val));
	}

	void encode(R value, WriteBuffer buffer, Consumer<T> writer);

	default <X> Encoding<T, X> extend(Encoding<R, X> extension) {
		Require.nonNull(extension);
		return (val, buf, writer) -> extension.encode(val, buf, r -> encode(r, buf, writer));
	}
}