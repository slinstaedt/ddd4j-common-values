package org.ddd4j.io.codec;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.util.collection.Array;
import org.ddd4j.util.value.Monad;
import org.ddd4j.util.value.Sequence;

@FunctionalInterface
public interface Decoding<T, R> {

	static <T, R> Decoding<T, R> choose(Function<ReadBuffer, Decoding<T, R>> choose) {
		Require.nonNull(choose);
		return (buffer, monad, value) -> choose.apply(buffer).decode(buffer, monad, value);
	}

	static <T> Decoding<Supplier<T>, T> readNullable() {
		return Decoding.<T>readOptional().extend((buffer, monad, reader) -> reader.get().map(Optional::get));
	}

	static <T> Decoding<Supplier<T>, Optional<T>> readOptional() {
		return (buffer, monad, reader) -> {
			if (buffer.getBoolean()) {
				return reader.get().map(Supplier::get).map(Optional::of);
			} else {
				return monad.create(Optional.empty());
			}
		};
	}

	static <T> Decoding<Supplier<T>, Sequence<T>> readSequence() {
		return (buffer, monad, reader) -> {
			int size = buffer.getUnsignedVarInt();
			if (size > 0) {
				return reader.get().map(r -> Array.ofSupplied(r, size));
			} else {
				return monad.create(Sequence.empty());
			}
		};
	}

	static <T> Decoding<Supplier<T>, T> readValue() {
		return (buffer, monad, reader) -> reader.get().map(Supplier::get);
	}

	static <T, R> Decoding<T, R> valueDeserialized(Function<ReadBuffer, ? extends R> deserializer) {
		Require.nonNull(deserializer);
		return (buffer, monad, lazy) -> monad.create(deserializer.apply(buffer));
	}

	static <T, R> Decoding<T, R> valueEmpty(R value) {
		return (buffer, monad, lazy) -> monad.create(value);
	}

	static <T, R> Decoding<T, R> valueRead(Function<? super T, ? extends R> constructor) {
		Require.nonNull(constructor);
		return (buffer, monad, lazy) -> lazy.get().map(constructor);
	}

	Monad<R> decode(ReadBuffer buffer, Monad.Factory monad, Supplier<Monad<T>> lazy);

	default <X> Decoding<T, X> extend(Decoding<R, X> extension) {
		Require.nonNull(extension);
		return (buf, mnd, t) -> extension.decode(buf, mnd, () -> this.decode(buf, mnd, t));
	}
}