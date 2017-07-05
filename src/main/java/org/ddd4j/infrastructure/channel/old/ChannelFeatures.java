package org.ddd4j.infrastructure.channel.old;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.ddd4j.Require;
import org.ddd4j.value.Nothing;
import org.ddd4j.value.function.Curry;
import org.ddd4j.value.function.Curry.Query.Ed;
import org.ddd4j.value.type.Instance;
import org.ddd4j.value.type.Type;

public class ChannelFeatures {

	public static class FeatureType<T> implements Type<Curry.Query.Ed<T, FeatureValue<T>>> {

		public static <T> FeatureType<T> newFeature() {
			return new FeatureType<>();
		}

		@Override
		public Ed<T, FeatureValue<T>> bind(Type<Ed<T, FeatureValue<T>>> param) {
			return v -> new FeatureValue<>(this, v);
		}

		public FeatureValue<T> withValue(T value) {
			return constructor().invoke(value);
		}
	}

	public static class FeatureValue<T> implements Instance {

		private final FeatureType<T> feature;
		private final T value;

		public FeatureValue(FeatureType<T> feature, T value) {
			this.feature = Require.nonNull(feature);
			this.value = Require.nonNull(value);
		}

		@Override
		public FeatureType<T> getType() {
			return feature;
		}

		public T getValue() {
			return value;
		}
	}

	public static final FeatureType<Nothing> CONCURRENT = FeatureType.newFeature();
	public static final FeatureType<Nothing> PERSISTENT = FeatureType.newFeature();
	public static final FeatureType<Nothing> PERSISTENT_RETAIN_LAST_STATE = FeatureType.newFeature();
	public static final FeatureType<Long> PERSISTENT_RETENTION_BY_SIZE = FeatureType.newFeature();
	public static final FeatureType<Duration> PERSISTENT_RETENTION_BY_TIME = FeatureType.newFeature();

	private final Map<FeatureType<?>, FeatureValue<?>> haveTos;
	private final Set<FeatureType<?>> mustNots;

	public ChannelFeatures() {
		this.haveTos = new HashMap<>();
		this.mustNots = new HashSet<>();
	}

	public ChannelFeatures mustNot(FeatureType<?> feature) {
		Require.that(!haveTos.keySet().contains(feature));
		mustNots.add(feature);
		return this;
	}

	public ChannelFeatures require(FeatureValue<?> feature) {
		Require.that(!mustNots.contains(feature.getType()));
		haveTos.put(feature.getType(), feature);
		return this;
	}
}
