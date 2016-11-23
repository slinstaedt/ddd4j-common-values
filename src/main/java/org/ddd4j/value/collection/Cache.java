package org.ddd4j.value.collection;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing;

public class Cache<K, V> implements Serializable {

	@FunctionalInterface
	public interface Factory<K, V> {

		V newInstance(K key);
	}

	@FunctionalInterface
	public interface KeyLookupStrategy {

		static <K> K equals(NavigableSet<K> keys, K key) {
			return keys.contains(key) ? key : null;
		}

		static <K> K first(NavigableSet<K> keys, K key) {
			return keys.isEmpty() ? null : keys.first();
		}

		static <K> K last(NavigableSet<K> keys, K key) {
			return keys.isEmpty() ? null : keys.last();
		}

		KeyLookupStrategy EQUALS = KeyLookupStrategy::equals;
		KeyLookupStrategy FIRST = KeyLookupStrategy::first;
		KeyLookupStrategy LAST = KeyLookupStrategy::last;
		KeyLookupStrategy CEILING = NavigableSet::ceiling;
		KeyLookupStrategy HIGHER = NavigableSet::higher;
		KeyLookupStrategy FLOOR = NavigableSet::floor;
		KeyLookupStrategy LOWER = NavigableSet::lower;

		<K> K apply(NavigableSet<K> keys, K key);

		default <K> K find(NavigableSet<K> keys, K key) {
			K found = apply(keys, key);
			return found != null ? found : key;
		}
	}

	public interface UsageStrategy<K, V> {

		class EvictionStratey<K, V> implements UsageStrategy<K, V> {

			private class Entry {

				private final K key;
				private final V value;
				private final long inserted;
				private long accessed;

				Entry(K key, V value) {
					this.key = Require.nonNull(key);
					this.value = Require.nonNull(value);
					this.inserted = System.currentTimeMillis();
				}

				V value() {
					accessed = System.currentTimeMillis();
					return value;
				}
			}

			private UsageStrategy<K, Entry> delegate;

			@Override
			public NavigableSet<K> keys() {
				return delegate.keys();
			}

			@Override
			public V acquire(K key, Factory<K, V> factory) {
				return delegate.acquire(key, k -> new Entry(k, factory.newInstance(k))).value();
			}

			@Override
			public boolean evict(K key, V value) {
				return delegate.;
			}

			@Override
			public void release(K key, V value) {
				delegate.release(key, value);
			}
		}

		class Exclusive<K, V> implements UsageStrategy<K, V> {
		}

		class Pooled<K, V> implements UsageStrategy<K, V> {

			private final ConcurrentNavigableMap<K, BlockingQueue<V>> pool;
			private IntFunction<BlockingQueue<V>> queueFactory;
			private int capacity;
			private long waitTimeout;
			private TimeUnit waitUnit;

			public Pooled(Comparator<? super K> comparator) {
				this.pool = new ConcurrentSkipListMap<>(comparator);
			}

			@Override
			public NavigableSet<K> keys() {
				return pool.keySet();
			}

			@Override
			public V acquire(K key, Factory<K, V> factory) {
				BlockingQueue<V> queue = pool.get(key);
				if (queue != null) {
					try {
						V value = queue.poll(waitTimeout, waitUnit);
						if (value != null) {
							return value;
						} else {
							throw new TimeoutException();
						}
					} catch (InterruptedException | TimeoutException e) {
						return Throwing.unchecked(e);
					}
				}
			}

			@Override
			public void release(K key, V value) {
				pool.compute(key, (k, q) -> {
					if (q == null) {
						q = queueFactory.apply(capacity);
					}
					q.offer(value);
					return q;
				});
			}
		}

		class Shared<K, V> implements UsageStrategy<K, V> {

			private final ConcurrentNavigableMap<K, Future<V>> singletons;
			private long waitTimeout;
			private TimeUnit waitUnit;
			private boolean retry;

			public Shared(Comparator<? super K> comparator) {
				this.singletons = new ConcurrentSkipListMap<>(comparator);
			}

			@Override
			public NavigableSet<K> keys() {
				return singletons.keySet();
			}

			@Override
			public V acquire(K key, Factory<K, V> factory) {
				Future<V> f = singletons.get(key);
				if (f == null) {
					FutureTask<V> task = new FutureTask<>(() -> factory.newInstance(key));
					f = singletons.putIfAbsent(key, task);
					if (f == null) {
						f = task;
						task.run();
					}
				}
				try {
					return f.get(waitTimeout, waitUnit);
				} catch (Exception e) {
					singletons.remove(key, f);
					if (e instanceof TimeoutException) {
						f.cancel(true);
					}
					return retry ? null : Throwing.unchecked(e);
				}
			}

			@Override
			public boolean evict(K key, V value) {
				return singletons.remove(key, value);
			}

			@Override
			public void release(K key, V value) {
				Require.that(singletons.containsKey(key));
			}
		}

		NavigableSet<K> keys();

		boolean evict(K key, V value);

		V acquire(K key, Factory<K, V> factory);

		void release(K key, V value);
	}

	private static final long serialVersionUID = 1L;

	public static <K, V> Cache<K, V> createPooled(Function<? super V, ? extends K> keyMapper) {
		return new Cache<>(null);
	}

	private Function<? super V, ? extends K> keyMapper;
	private final UsageStrategy<K, V> usageStrategy;
	private KeyLookupStrategy keyLookupStrategy;

	private Factory<K, V> factory;

	public Cache(UsageStrategy<K, V> strategy) {
		this.usageStrategy = requireNonNull(strategy);
	}

	public V acquire(K key) {
		V value = null;
		while (value == null) {
			K effectiveKey = keyLookupStrategy.find(usageStrategy.keys(), key);
			value = usageStrategy.acquire(effectiveKey, factory);
		}
		return value;
	}

	public void release(V value) {
		usageStrategy.release(keyMapper.apply(value), value);
	}
}
