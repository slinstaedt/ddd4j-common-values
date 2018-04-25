package org.ddd4j.io.codec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.ddd4j.Require;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Array;
import org.ddd4j.util.Type;

@FunctionalInterface
public interface Decoder<T> {

	interface Factory {

		static <X> Decoder<X> failing(Type<X> readerType) {
			throw new IllegalArgumentException("Can not find decoder for " + readerType);
		}

		default <T> Decoder<T> decoder(Class<T> readerType) {
			return decoder(Type.of(readerType));
		}

		<T> Decoder<T> decoder(Type<T> readerType);
	}

	public class Registry implements Factory {

		private final Factory fallback;
		private final Map<Type<?>, Decoder<?>> decoders;

		private Registry(Factory fallback, Map<Type<?>, Decoder<?>> decoders) {
			this.fallback = Require.nonNull(fallback);
			this.decoders = Require.nonNull(decoders);
		}

		@Override
		public <T> Decoder<T> decoder(Type<T> readerType) {
			@SuppressWarnings("unchecked")
			Decoder<T> decoder = (Decoder<T>) decoders.get(readerType);
			if (decoder == null) {
				fallback.decoder(readerType);
			}
			return Require.nonNull(decoder);
		}

		public <T> Registry with(Class<T> type, Decoder<? extends T> decoder) {
			return with(Type.of(type), decoder);
		}

		public <T> Registry with(Type<T> type, Decoder<? extends T> decoder) {
			Map<Type<?>, Decoder<?>> copy = new HashMap<>(decoders);
			copy.put(Require.nonNull(type), Require.nonNull(decoder));
			return new Registry(fallback, copy);
		}
	}

	@FunctionalInterface
	interface Terminal<T> extends Decoder<T> {

		T decode(ReadBuffer buffer);

		@Override
		default T decode(ReadBuffer buffer, Factory factory) {
			return decode(buffer);
		}
	}

	class Union<T> implements Decoder<T> {

		private static final int MAX_UNIONS = 256;

		private final Array<Decoder<? extends T>> unions;

		private Union(Array<Decoder<? extends T>> unions) {
			this.unions = Require.nonNull(unions);
		}

		@Override
		public T decode(ReadBuffer buffer, Factory factory) {
			int index = Byte.toUnsignedInt(buffer.get());
			Decoder<? extends T> decoder = unions.get(index);
			if (decoder != null) {
				return decoder.decode(buffer, factory);
			} else {
				throw new IllegalArgumentException("No union registered for index: " + index);
			}
		}

		public Union<T> with(Decoder<? extends T> decoder) {
			Require.that(unions.size() < MAX_UNIONS);
			return new Union<>(unions.with(Require.nonNull(decoder)));
		}
	}

	static <T> Union<T> emptyUnion() {
		return new Union<>(new Array<>(0));
	}

	static <T> Union<T> nullableUnion() {
		return Decoder.<T>emptyUnion().with(nullDecoder());
	}

	static <T> Decoder<T> nullDecoder() {
		return (buf, fct) -> null;
	}

	static Registry registry(Factory fallback) {
		return new Registry(fallback, Collections.emptyMap());
	}

	T decode(ReadBuffer buffer, Factory factory);
}