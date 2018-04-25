package org.ddd4j.io.codec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

		default <T> Encoder<T> encoder(Class<T> writerType) {
			return encoder(Type.of(writerType));
		}

		<T> Encoder<T> encoder(Type<T> writerType);
	}

	public class Registry implements Factory {

		private final Factory fallback;
		private final Map<Type<?>, Encoder<?>> encoders;

		private Registry(Factory fallback, Map<Type<?>, Encoder<?>> encoders) {
			this.fallback = Require.nonNull(fallback);
			this.encoders = Require.nonNull(encoders);
		}

		@Override
		public <T> Encoder<T> encoder(Type<T> writerType) {
			@SuppressWarnings("unchecked")
			Encoder<T> encoder = (Encoder<T>) encoders.get(writerType);
			if (encoder == null) {
				fallback.encoder(writerType);
			}
			return Require.nonNull(encoder);
		}

		public <T> Registry with(Class<T> type, Encoder<? super T> encoder) {
			return with(Type.of(type), encoder);
		}

		public <T> Registry with(Type<T> type, Encoder<? super T> encoder) {
			Map<Type<?>, Encoder<?>> copy = new HashMap<>(encoders);
			copy.put(Require.nonNull(type), Require.nonNull(encoder));
			return new Registry(fallback, copy);
		}
	}

	@FunctionalInterface
	interface Terminal<T> extends Encoder<T> {

		void encode(T value, WriteBuffer buffer);

		@Override
		default void encode(T value, WriteBuffer buffer, Factory factory) {
			encode(value, buffer);
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

			boolean tryEncode(S value, WriteBuffer buffer, Factory factory) {
				return check.value(value).ifPositive(v -> encoder.encode(v, buffer.put(index), factory));
			}
		}

		private static final int MAX_UNIONS = 256;

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

	static <T> Union<T> emptyUnion() {
		return new Union<>(new Array<>(0));
	}

	static <T> Union<T> nullableUnion() {
		return Encoder.<T>emptyUnion().with(Check.isNull(), nullEncoder());
	}

	static <T> Encoder<T> nullEncoder() {
		return (val, buf, fct) -> Require.nonNull(val);
	}

	static Registry registry(Factory fallback) {
		return new Registry(fallback, Collections.emptyMap());
	}

	void encode(T value, WriteBuffer buffer, Factory factory);
}