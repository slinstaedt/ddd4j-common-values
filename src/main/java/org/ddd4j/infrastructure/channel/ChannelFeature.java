package org.ddd4j.infrastructure.channel;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.channel.ChannelFeature.Value;
import org.ddd4j.value.function.Curry;
import org.ddd4j.value.function.Curry.Query.Ed;
import org.ddd4j.value.type.Instance;
import org.ddd4j.value.type.Type;

public class ChannelFeature<T> implements Type<Curry.Query.Ed<T, Value<T>>> {

	public static class Value<T> implements Instance {

		private final ChannelFeature<T> feature;
		private final T value;

		public Value(ChannelFeature<T> feature, T value) {
			this.feature = Require.nonNull(feature);
			this.value = Require.nonNull(value);
		}

		@Override
		public ChannelFeature<T> getType() {
			return feature;
		}

		public T getValue() {
			return value;
		}
	}

	public static <T> ChannelFeature<T> newFeature() {
		return new ChannelFeature<>();
	}

	public Value<T> withValue(T value) {
		return constructor().invoke(value);
	}

	@Override
	public Ed<T, Value<T>> bind(Type<Ed<T, Value<T>>> param) {
		return v -> new Value<>(this, v);
	}
}
