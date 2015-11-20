package org.ddd4j.io;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.ddd4j.io.Cache.UsageStrategy.Shared;

public class Cache<K, V> implements Serializable {

	@FunctionalInterface
	public interface KeyLookupStragy {

		KeyLookupStragy EQUALS = KeyLookupStragy::equals;
		KeyLookupStragy FIRST = KeyLookupStragy::first;
		KeyLookupStragy LAST = KeyLookupStragy::last;
		KeyLookupStragy CEILING = NavigableSet::ceiling;
		KeyLookupStragy HIGHER = NavigableSet::higher;
		KeyLookupStragy FLOOR = NavigableSet::floor;
		KeyLookupStragy LOWER = NavigableSet::lower;

		static <K> K equals(NavigableSet<K> keys, K key) {
			return keys.contains(key) ? key : null;
		}

		static <K> K first(NavigableSet<K> keys, K key) {
			return keys.isEmpty() ? null : keys.first();
		}

		static <K> K last(NavigableSet<K> keys, K key) {
			return keys.isEmpty() ? null : keys.last();
		}

		<K> K apply(NavigableSet<K> keys, K key);
	}

	public interface UsageStrategy<K, V> {

		class Exclusive<K, V> implements UsageStrategy<K, V> {

			private final Function<? super V, ? extends K> keyMapper;

			public Exclusive(Function<? super V, ? extends K> keyMapper) {
				this.keyMapper = requireNonNull(keyMapper);
			}

			@Override
			public BiFunction<ConcurrentMap<K, V>, ? super K, ? extends V> acquire() {
				return (m, k) -> m.remove(k);
			}

			@Override
			public BiFunction<ConcurrentMap<K, V>, ? super V, ?> release() {
				return (m, v) -> m.putIfAbsent(keyMapper.apply(v), v);
			}
		}

		class Pooled<K, V> implements UsageStrategy<K, V> {

			private final Function<? super V, ? extends K> keyMapper;

			public Pooled(Function<? super V, ? extends K> keyMapper) {
				this.keyMapper = requireNonNull(keyMapper);
			}

			@Override
			public BiFunction<ConcurrentMap<K, V>, ? super K, ? extends V> acquire() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public BiFunction<ConcurrentMap<K, V>, ? super V, ?> release() {
				// TODO Auto-generated method stub
				return null;
			}
		}

		class Shared<K, V> implements UsageStrategy<K, V> {

			@Override
			public BiFunction<ConcurrentMap<K, V>, ? super K, ? extends V> acquire() {
				return Map::get;
			}

			@Override
			public BiFunction<ConcurrentMap<K, V>, ? super V, ?> release() {
				return Map::containsKey;
			}
		}

		BiFunction<ConcurrentMap<K, V>, ? super K, ? extends V> acquire();

		BiFunction<ConcurrentMap<K, V>, ? super V, ?> release();
	}

	private static final long serialVersionUID = 1L;

	public static <K, V> Cache<K, V> createPooled(Function<? super V, ? extends K> keyMapper) {
		return new Cache<>(new Shared<>(), null);
	}

	private final ConcurrentNavigableMap<K, V> entries;
	private final UsageStrategy<K, V> strategy;
	private KeyLookupStragy keyLookupStragy;

	private Function<? super K, ? extends V> factory;

	public Cache(UsageStrategy<K, V> strategy, Comparator<? super K> comparator) {
		this.entries = new ConcurrentSkipListMap<>(comparator);
		this.strategy = requireNonNull(strategy);
	}

	public V acquire(final K key) {
		V value = null;
		while (value == null) {
			K effectiveKey = keyLookupStragy.apply(entries.keySet(), key);
			if (effectiveKey != null) {
				value = strategy.acquire().apply(entries, effectiveKey);
			} else {
				release(factory.apply(key));
			}
		}
		return value;
	}

	public void release(V value) {
		strategy.release().apply(entries, value);
	}
}
