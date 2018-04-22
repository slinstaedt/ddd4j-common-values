package org.ddd4j.infrastructure.domain.header;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Array;

public class HeaderKey<T> {

	private static class SerDe<T> {

		private final BiConsumer<? super T, WriteBuffer> serializer;
		private final Function<ReadBuffer, ? extends T> deserializer;

		SerDe(BiConsumer<? super T, WriteBuffer> serializer, Function<ReadBuffer, ? extends T> deserializer) {
			this.serializer = Require.nonNull(serializer);
			this.deserializer = Require.nonNull(deserializer);
		}

		T deserialize(ReadBuffer buffer) {
			return deserializer.apply(buffer);
		}

		WriteBuffer serialize(T value, WriteBuffer buffer) {
			serializer.accept(value, buffer);
			return buffer;
		}
	}

	private static final int MAX_VERSIONS = Byte.toUnsignedInt(Byte.MIN_VALUE);

	public static <T> HeaderKey<T> of(String name, BiConsumer<? super T, WriteBuffer> serializer,
			Function<ReadBuffer, ? extends T> deserializer) {
		return new HeaderKey<>(name, new Array<SerDe<T>>(1).add(new SerDe<>(serializer, deserializer)));
	}

	private final String name;
	private final Array<SerDe<T>> serDes;

	private HeaderKey(String name, Array<SerDe<T>> serDes) {
		this.name = Require.nonEmpty(name);
		this.serDes = Require.that(serDes, Array::isNotEmpty);
	}

	T deserialize(ReadBuffer buffer) {
		int version = Byte.toUnsignedInt(buffer.get());
		return serDes.getOptional(version)
				.orElseThrow(() -> new IllegalArgumentException(
						"No deserializer registered with version '" + version + "' for header '" + name + "'"))
				.deserialize(buffer);
	}

	public String getName() {
		return name;
	}

	public int getVersions() {
		return serDes.size();
	}

	WriteBuffer serialize(T value, WriteBuffer buffer) {
		int latest = getVersions() - 1;
		buffer.put((byte) latest);
		return serDes.get(latest).serialize(value, buffer);
	}

	public HeaderKey<T> withNew(BiConsumer<? super T, WriteBuffer> serializer, Function<ReadBuffer, ? extends T> deserializer) {
		Require.that(getVersions() < MAX_VERSIONS);
		return new HeaderKey<>(name, serDes.with(new SerDe<T>(serializer, deserializer)));
	}
}
