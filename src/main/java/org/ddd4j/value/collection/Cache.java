package org.ddd4j.value.collection;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.ddd4j.contract.Require;

public class Cache<K, V> implements Serializable {

	@FunctionalInterface
	public interface Factory<K, V> {

		V newInstance(K key) throws Exception;
	}

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
			private ConcurrentMap<V, Entry> entries;

			@Override
			public Optional<V> acquire(K key, Callable<? extends V> producer) throws Exception {
				return delegate.acquire(key, () -> new Entry(key, producer.call())).value();
			}

			@Override
			public boolean evict(K key, V value) {
				return delegate.evict(key, entries.get(value));
			}

			@Override
			public NavigableSet<K> keys() {
				return delegate.keys();
			}

			@Override
			public void release(K key, V value) {
				delegate.release(key, entries.get(value));
			}

			@Override
			public int size() {
				return delegate.size();
			}
		}

		class CapacityStrategy<K, V> implements UsageStrategy<K, V> {

			private final UsageStrategy<K, V> delegate;
			private final long timeoutInMillis;
			private final Semaphore semaphore;

			public CapacityStrategy(UsageStrategy<K, V> delegate, long timeoutInMillis, int capacity, boolean fair) {
				this.delegate = Require.nonNull(delegate);
				this.timeoutInMillis = Require.that(timeoutInMillis, timeoutInMillis >= 0);
				this.semaphore = new Semaphore(capacity, fair);
			}

			@Override
			public Optional<V> acquire(K key, Callable<? extends V> producer) throws Exception {
				final long started = System.currentTimeMillis();
				Optional<V> result = Optional.empty();
				long waitInMillis;
				do {
					waitInMillis = timeoutInMillis + started - System.currentTimeMillis();
					if (semaphore.tryAcquire(waitInMillis, TimeUnit.MILLISECONDS)) {
						result = delegate.acquire(key, producer);
						if (!result.isPresent()) {
							semaphore.release();
						}
					}
				} while (!result.isPresent() && waitInMillis > 0);
				return result;
			}

			@Override
			public boolean evict(K key, V value) {
				return delegate.evict(key, value);
			}

			@Override
			public NavigableSet<K> keys() {
				return delegate.keys();
			}

			@Override
			public void release(K key, V value) {
				delegate.release(key, value);
				semaphore.release();
			}

			@Override
			public int size() {
				return delegate.size();
			}
		}

		class Exclusive<K, V> implements UsageStrategy<K, V> {

			@Override
			public Optional<V> acquire(K key, Callable<? extends V> producer) throws Exception {
				return Optional.of(producer.call());
			}

			@Override
			public boolean evict(K key, V value) {
				return false;
			}

			@Override
			public NavigableSet<K> keys() {
				return Collections.emptyNavigableSet();
			}

			@Override
			public void release(K key, V value) {
			}

			@Override
			public int size() {
				return 0;
			}
		}

		class Pooled<K, V> implements UsageStrategy<K, V> {

			private final ConcurrentNavigableMap<K, BlockingQueue<V>> pool;
			private Supplier<BlockingQueue<V>> queueFactory;

			public Pooled(Comparator<? super K> comparator) {
				this.pool = new ConcurrentSkipListMap<>(comparator);
			}

			@Override
			public Optional<V> acquire(K key, Callable<? extends V> producer) throws Exception {
				BlockingQueue<V> queue = pool.get(key);
				if (queue != null) {
					V value = queue.poll();
					if (value != null) {
						pool.computeIfPresent(key, (k, q) -> q.isEmpty() ? null : q);
					} else {
						value = producer.call();
					}
					return Optional.ofNullable(value);
				} else {
					return Optional.empty();
				}
			}

			@Override
			public boolean evict(K key, V value) {
				return pool.remove(key, value);
			}

			@Override
			public NavigableSet<K> keys() {
				return pool.keySet();
			}

			@Override
			public synchronized void release(K key, V value) {
				pool.compute(key, (k, q) -> {
					if (q == null) {
						q = queueFactory.get();
					}
					q.offer(value);
					return q;
				});
				notify();
			}

			@Override
			public int size() {
				return pool.values().stream().mapToInt(Queue::size).sum();
			}
		}

		class Shared<K, V> implements UsageStrategy<K, V> {

			private final ConcurrentNavigableMap<K, Future<V>> singletons;

			public Shared(Comparator<? super K> comparator) {
				this.singletons = new ConcurrentSkipListMap<>(comparator);
			}

			@Override
			public Optional<V> acquire(K key, Callable<? extends V> producer) throws Exception {
				Future<V> f = singletons.get(key);
				if (f == null) {
					FutureTask<V> task = new FutureTask<>(producer::call);
					f = singletons.putIfAbsent(key, task);
					if (f == null) {
						f = task;
						task.run();
					}
				}
				return f.isDone() ? Optional.of(f.get()) : Optional.empty();
			}

			@Override
			public boolean evict(K key, V value) {
				return singletons.remove(key, value);
			}

			@Override
			public NavigableSet<K> keys() {
				return singletons.keySet();
			}

			@Override
			public void release(K key, V value) {
			}

			@Override
			public int size() {
				return singletons.size();
			}
		}

		Optional<V> acquire(K key, Callable<? extends V> producer) throws Exception;

		boolean evict(K key, V value);

		NavigableSet<K> keys();

		void release(K key, V value);

		int size();
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
		return acquire(key, () -> factory.newInstance(key));
	}

	public V acquire(K key, Callable<? extends V> producer) {
		V value = null;
		while (value == null) {
			K effectiveKey = keyLookupStrategy.find(usageStrategy.keys(), key);
			value = usageStrategy.acquire(effectiveKey, producer);
		}
		return value;
	}

	public void release(V value) {
		usageStrategy.release(keyMapper.apply(value), value);
	}
}
