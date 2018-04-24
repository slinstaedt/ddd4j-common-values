package org.ddd4j.io.codec;

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

		default <T> Decoder<T> nonNull(Class<T> readerType) {
			return nonNull(Type.of(readerType));
		}

		<T> Decoder<T> nonNull(Type<T> readerType);

		default <T> Decoder<T> nullable(Class<T> readerType) {
			return Union.<T>nullable().with(nonNull(readerType));
		}

		default <T> Decoder<T> nullable(Type<T> readerType) {
			return Union.<T>nullable().with(nonNull(readerType));
		}
	}

	@FunctionalInterface
	interface Terminal<T> extends Decoder<T> {

		T decode(ReadBuffer buffer);

		@Override
		default T decode(ReadBuffer buffer, Decoder.Factory factory) {
			return decode(buffer);
		}
	}

	class Union<T> implements Decoder<T> {

		private static final int MAX_UNIONS = 256;

		public static <T> Union<T> empty() {
			return new Union<>(new Array<>(0));
		}

		public static <T> Union<T> nullable() {
			return Union.<T>empty().with(Codec.nullable());
		}

		private final Array<Decoder<? extends T>> unions;

		private Union(Array<Decoder<? extends T>> unions) {
			this.unions = Require.nonNull(unions);
		}

		@Override
		public T decode(ReadBuffer buffer, Decoder.Factory factory) {
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

	T decode(ReadBuffer buffer, Decoder.Factory factory);
}