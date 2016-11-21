package org.ddd4j.value.collection;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;

public class Cache<K, V> implements Serializable {

	@FunctionalInterface
	public interface KeyLookupStrategy {

		KeyLookupStrategy EQUALS = KeyLookupStrategy::equals;
		KeyLookupStrategy FIRST = KeyLookupStrategy::first;
		KeyLookupStrategy LAST = KeyLookupStrategy::last;
		KeyLookupStrategy CEILING = NavigableSet::ceiling;
		KeyLookupStrategy HIGHER = NavigableSet::higher;
		KeyLookupStrategy FLOOR = NavigableSet::floor;
		KeyLookupStrategy LOWER = NavigableSet::lower;

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
		}

		class Pooled<K, V> implements UsageStrategy<K, V> {

			private final ConcurrentNavigableMap<K, Seq<V>> pool;

			public Pooled(Comparator<? super K> comparator) {
				this.pool = new ConcurrentSkipListMap<>(comparator);
			}

			@Override
			public NavigableSet<K> keys() {
				return pool.keySet();
			}

			@Override
			public V acquire(K key) {
				Ref<V> ref = Ref.create();
				pool.computeIfPresent(key, (k, s) -> s.filter().splitAtHead().visitLeft(ref::setOptional).getRight());
				return ref.get();
			}

			@Override
			public void release(K key, V value) {
				pool.compute(key, (k, s) -> s != null ? s.append().entry(value) : Seq.of(value));
			}
		}

		class Shared<K, V> implements UsageStrategy<K, V> {

			private final ConcurrentNavigableMap<K, V> singletons;

			public Shared(Comparator<? super K> comparator) {
				this.singletons = new ConcurrentSkipListMap<>(comparator);
			}

			@Override
			public NavigableSet<K> keys() {
				return singletons.keySet();
			}

			@Override
			public V acquire(K key) {
				return singletons.get(key);
			}

			@Override
			public void release(K key, V value) {
				singletons.putIfAbsent(key, value);
			}
		}

		NavigableSet<K> keys();

		V acquire(K key);

		void release(K key, V value);
	}

	private static final long serialVersionUID = 1L;

	public static <K, V> Cache<K, V> createPooled(Function<? super V, ? extends K> keyMapper) {
		return new Cache<>(null);
	}

	private Function<? super V, ? extends K> keyMapper;
	private final UsageStrategy<K, V> usageStrategy;
	private KeyLookupStrategy keyLookupStrategy;

	private Function<? super K, ? extends V> factory;

	public Cache(UsageStrategy<K, V> strategy) {
		this.usageStrategy = requireNonNull(strategy);
	}

	public V acquire(K key) {
		V value = null;
		while (value == null) {
			K effectiveKey = keyLookupStrategy.apply(usageStrategy.keys(), key);
			if (effectiveKey != null) {
				value = usageStrategy.acquire(effectiveKey);
			} else {
				release(factory.apply(key));
			}
		}
		return value;
	}

	public void release(V value) {
		usageStrategy.release(keyMapper.apply(value), value);
	}
}
