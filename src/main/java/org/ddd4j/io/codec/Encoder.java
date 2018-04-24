package org.ddd4j.io.codec;

import org.ddd4j.Require;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Array;
import org.ddd4j.util.Check;
import org.ddd4j.util.Type;

@FunctionalInterface
public interface Encoder<T> {

	interface Factory {

		static <X> Encoder<X> failing(Type<X> writerType) {
			throw new IllegalArgumentException("Can not find encoder for " + writerType);
		}

		default <T> Encoder<T> nonNull(Class<T> writerType) {
			return nonNull(Type.of(writerType));
		}

		<T> Encoder<T> nonNull(Type<T> writerType);

		default <T> Encoder<T> nullable(Class<T> writerType) {
			return Union.<T>nullable().with(writerType, nonNull(writerType));
		}

		default <T> Encoder<T> nullable(Type<T> writerType) {
			return Union.<T>nullable().with(writerType.getRawType(), nonNull(writerType));
		}
	}

	class Union<T> implements Encoder<T> {

		private static class Entry<S, T extends S> {

			private final Check<? super S, ? extends T> check;
			private final Encoder<? super T> encoder;
			private final byte index;

			Entry(Check<? super S, ? extends T> check, Encoder<? super T> encoder, int index) {
				this.check = Require.nonNull(check);
				this.encoder = Require.nonNull(encoder);
				this.index = (byte) index;
			}

			boolean tryEncode(S value, WriteBuffer buffer, Encoder.Factory factory) {
				return check.value(value).ifPositive(v -> encoder.encode(v, buffer.put(index), factory));
			}
		}

		private static final int MAX_UNIONS = 256;

		public static <T> Union<T> empty() {
			return new Union<>(new Array<>(0));
		}

		public static <T> Union<T> nullable() {
			return Union.<T>empty().with(Check.isNull(), Codec.nullable());
		}

		private final Array<Entry<T, ?>> unions;

		private Union(Array<Entry<T, ?>> unions) {
			this.unions = Require.nonNull(unions);
		}

		@Override
		public void encode(T value, WriteBuffer buffer, Factory factory) {
			boolean present = unions.stream().filter(u -> u.tryEncode(value, buffer, factory)).findFirst().isPresent();
			if (!present) {
				throw new IllegalArgumentException("No union registered for value: " + value);
			}
		}

		public <X extends T> Union<T> with(Check<? super T, ? extends X> check, Encoder<? super X> encoder) {
			Require.that(unions.size() < MAX_UNIONS);
			return new Union<>(unions.with(new Entry<>(check, encoder, unions.size() + 1)));
		}

		public <X extends T> Union<T> with(Class<? extends X> type, Encoder<? super T> encoder) {
			return with(Check.is(type), encoder);
		}
	}

	void encode(T value, WriteBuffer buffer, Encoder.Factory factory);
}