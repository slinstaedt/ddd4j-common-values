package org.ddd4j.io.codec;

import org.ddd4j.Require;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Check;
import org.ddd4j.util.Type;

public interface Codec<T> extends Decoder<T>, Encoder<T> {

	interface Factory extends Decoder.Factory, Encoder.Factory {

		static <X> Codec<X> failing(Type<X> type) {
			throw new IllegalArgumentException("Can not find codec for " + type);
		}

		static Codec.Factory of(Decoder.Factory decoder, Encoder.Factory encoder) {
			Require.nonNulls(decoder, encoder);
			return new Factory() {

				@Override
				public <T> Codec<T> nonNull(Type<T> type) {
					return Codec.of(decoder.nonNull(type), encoder.nonNull(type));
				}
			};
		}

		@Override
		default <T> Codec<T> nonNull(Class<T> type) {
			return nonNull(Type.of(type));
		}

		@Override
		<T> Codec<T> nonNull(Type<T> type);

		@Override
		default <T> Codec<T> nullable(Class<T> type) {
			return Union.<T>nullable().with(type, nonNull(type));
		}

		@Override
		default <T> Codec<T> nullable(Type<T> type) {
			return Union.<T>nullable().with(type.getRawType(), nonNull(type));
		}
	}

	class Union<T> implements Codec<T> {

		public static <T> Union<T> empty() {
			return new Union<>(Decoder.Union.empty(), Encoder.Union.empty());
		}

		public static <T> Union<T> nullable() {
			return Union.<T>empty().with(Check.isNull(), Codec.nullable());
		}

		private final Decoder.Union<T> decoders;
		private final Encoder.Union<T> encoders;

		private Union(Decoder.Union<T> decoders, Encoder.Union<T> encoders) {
			this.decoders = Require.nonNull(decoders);
			this.encoders = Require.nonNull(encoders);
		}

		@Override
		public T decode(ReadBuffer buffer, Decoder.Factory factory) {
			return decoders.decode(buffer, factory);
		}

		@Override
		public void encode(T value, WriteBuffer buffer, Encoder.Factory factory) {
			encoders.encode(value, buffer, factory);
		}

		public <X extends T> Union<T> with(Check<? super T, ? extends X> check, Codec<X> codec) {
			return new Union<>(decoders.with(codec), encoders.with(check, codec));
		}

		public <X extends T> Union<T> with(Check<? super T, ? extends X> check, Decoder<? extends X> decoder, Encoder<? super X> encoder) {
			return with(check, Codec.of(decoder, encoder));
		}

		public <X extends T> Union<T> with(Class<? extends X> type, Codec<X> codec) {
			return with(Check.is(type), codec);
		}

		public <X extends T> Union<T> with(Class<? extends X> type, Decoder<? extends X> decoder, Encoder<? super X> encoder) {
			return with(Check.is(type), Codec.of(decoder, encoder));
		}
	}

	static <T> Codec<T> nullable() {
		return of((buf, fct) -> null, (val, buf, fct) -> Require.nonNull(val));
	}

	static <T> Codec<T> of(Decoder<? extends T> decoder, Encoder<? super T> encoder) {
		Require.nonNulls(decoder, encoder);
		return new Codec<T>() {

			@Override
			public T decode(ReadBuffer buffer, Decoder.Factory factory) {
				return decoder.decode(buffer, factory);
			}

			@Override
			public void encode(T value, WriteBuffer buffer, Encoder.Factory factory) {
				encoder.encode(value, buffer, factory);
			}
		};
	}

}