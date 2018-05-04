package org.ddd4j.infrastructure.codec;

import java.util.function.Supplier;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing.TFunction;
import org.ddd4j.value.versioned.Revision;

@FunctionalInterface
public interface Decoder<T> {

	@FunctionalInterface
	interface Extender<T, S> {

		interface Wrapper<T, S> {

			static <T, S> Wrapper<T, S> valueDeserialized(TFunction<ReadBuffer, ? extends S> deserializer) {
				return (buf, t) -> Promise.completed(deserializer.apply(buf));
			}

			static <T, S> Wrapper<T, S> valueEmpty(S value) {
				return (buf, t) -> Promise.completed(value);
			}

			static <T, S> Wrapper<T, S> valuePresent(TFunction<? super T, ? extends S> constructor) {
				return (buf, t) -> t.get().thenApply(constructor);
			}

			Promise<S> wrap(ReadBuffer buffer, Supplier<Promise<T>> promise);
		}

		static <X> Extender<X, X> none() {
			return buf -> Wrapper.valuePresent(t -> t);
		}

		Wrapper<T, S> wrapper(ReadBuffer buffer);
	}

	Promise<T> decode(ReadBuffer buffer, Revision revision);

	default <X> Decoder<X> extend(Extender<T, X> extender) {
		Require.nonNull(extender);
		return (buf, rev) -> extender.wrapper(buf).wrap(buf, () -> decode(buf, rev));
	}
}