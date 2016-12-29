package org.ddd4j.infrastructure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Cache.Access.Exclusive;
import org.ddd4j.infrastructure.Cache.Access.Shared;
import org.ddd4j.infrastructure.Cache.Decorating.Blocking;
import org.ddd4j.infrastructure.Cache.Decorating.Evicting;
import org.ddd4j.infrastructure.Cache.Decorating.Evicting.EvictStrategy;
import org.ddd4j.infrastructure.Cache.Decorating.Retrying;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.Throwing.TSupplier;

public interface Cache<K, V> {

	abstract class Access<K, V> {

		public static class Exclusive<K, V> extends Access<K, V> {

			private final ConcurrentNavigableMap<K, Queue<V>> pool;
			private final Function<? super V, ? extends K> keyedBy;
			private final Queue<Queue<V>> unusedQueues;

			Exclusive(Comparator<? super K> comparator, Function<? super V, ? extends K> keyedBy) {
				this.pool = new ConcurrentSkipListMap<>(comparator);
				this.keyedBy = Require.nonNull(keyedBy);
				this.unusedQueues = new ArrayDeque<>();
			}

			@Override
			V acquire(K key, TSupplier<? extends V> supplier, long timeoutInMillis) {
				V value = null;
				Queue<V> queue = pool.get(key);
				if (queue != null) {
					value = queue.poll();
					if (queue.isEmpty() && pool.remove(key, queue)) {
						unusedQueues.offer(queue);
					}
				}
				return value != null ? value : supplier.get();
			}

			private Queue<V> enqueue(Queue<V> queue, V value) {
				if (queue == null) {
					queue = unusedQueues.poll();
					if (queue == null) {
						queue = new ConcurrentLinkedQueue<>();
					}
				}
				queue.offer(value);
				return queue;
			}

			@Override
			boolean evict(K key, V value) {
				Queue<V> queue = pool.get(key);
				return queue != null ? queue.remove(value) : false;
			}

			@Override
			NavigableSet<K> keys() {
				return pool.keySet();
			}

			@Override
			void release(V value) {
				pool.compute(keyedBy.apply(value), (k, q) -> enqueue(q, value));
			}
		}

		public static class Shared<K, V> extends Access<K, V> {

			private final ConcurrentNavigableMap<K, Future<V>> singletons;
			private final ConcurrentMap<V, Future<V>> futures;

			Shared(Comparator<? super K> comparator) {
				this.singletons = new ConcurrentSkipListMap<>(comparator);
				this.futures = new ConcurrentHashMap<>();
			}

			@Override
			V acquire(K key, TSupplier<? extends V> supplier, long timeoutInMillis) {
				Future<V> f = singletons.get(key);
				if (f == null) {
					FutureTask<V> task = new FutureTask<>(supplier::getChecked);
					f = singletons.putIfAbsent(key, task);
					if (f == null) {
						f = task;
						task.run();
						try {
							futures.put(task.get(), task);
						} catch (Exception e) {
						}
					}
				}
				try {
					return f.get(timeoutInMillis, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					return Throwing.unchecked(e);
				} catch (ExecutionException | TimeoutException e) {
					return null;
				}
			}

			@Override
			boolean evict(K key, V value) {
				Future<V> future = futures.remove(value);
				return future != null ? singletons.remove(key, future) : false;
			}

			@Override
			NavigableSet<K> keys() {
				return singletons.keySet();
			}

			@Override
			void release(V value) {
			}
		}

		abstract V acquire(K key, TSupplier<? extends V> supplier, long timeoutInMillis);

		public Access<K, V> blockOn(int capacity, boolean fair) {
			return new Blocking<>(this, capacity, fair);
		}

		public Evicting<K, V> evict() {
			return evict(EvictStrategy.ANY);
		}

		public Evicting<K, V> evict(EvictStrategy strategy) {
			return new Evicting<>(this, strategy, 0, Integer.MAX_VALUE, Long.MAX_VALUE);
		}

		abstract boolean evict(K key, V value);

		abstract NavigableSet<K> keys();

		public Aside<K, V> lookupValues(KeyLookupStrategy keyLookup) {
			return new Aside<>(this, keyLookup);
		}

		public Aside<K, V> lookupValuesWithEqualKeys() {
			return new Aside<>(this, KeyLookupStrategy.EQUALS);
		}

		abstract void release(V value);

		public Retrying<K, V> retry() {
			return new Retrying<>(this, 0, Long.MAX_VALUE);
		}
	}

	class Aside<K, V> implements Cache<K, V> {

		private final Access<K, V> access;
		private final KeyLookupStrategy keyLookup;

		Aside(Access<K, V> access, KeyLookupStrategy keyLookup) {
			this.access = Require.nonNull(access);
			this.keyLookup = Require.nonNull(keyLookup);
		}

		public V acquire(K key, TSupplier<? extends V> supplier) {
			return acquire(key, supplier, Long.MAX_VALUE);
		}

		public V acquire(K key, TSupplier<? extends V> supplier, long timeoutInMillis) {
			K effectiveKey = keyLookup.find(access.keys(), key);
			return access.acquire(effectiveKey, supplier, timeoutInMillis);
		}

		@Override
		public void release(V value) {
			access.release(value);
		}

		public ReadThrough<K, V> withFactory(Factory<? super K, ? extends V> factory) {
			return new ReadThrough<>(this, factory);
		}
	}

	abstract class Decorating<K, V> extends Access<K, V> {

		public static class Blocking<K, V> extends Decorating<K, V> {

			private final Access<K, V> delegate;
			private final Semaphore semaphore;

			public Blocking(Access<K, V> delegate, int capacity, boolean fair) {
				this.delegate = Require.nonNull(delegate);
				this.semaphore = new Semaphore(capacity, fair);
			}

			@Override
			V acquire(K key, TSupplier<? extends V> supplier, long timeoutInMillis) {
				final long started = System.currentTimeMillis();
				V value = null;
				long waitInMillis;
				do {
					waitInMillis = timeoutInMillis + started - System.currentTimeMillis();
					try {
						if (semaphore.tryAcquire(waitInMillis, TimeUnit.MILLISECONDS)) {
							waitInMillis = timeoutInMillis + started - System.currentTimeMillis();
							value = delegate.acquire(key, supplier, waitInMillis);
							if (value == null) {
								semaphore.release();
							}
						}
					} catch (InterruptedException e) {
						return Throwing.unchecked(e);
					}
				} while (value == null && waitInMillis > 0);
				return value;
			}

			@Override
			boolean evict(K key, V value) {
				if (delegate.evict(key, value)) {
					semaphore.release();
					return true;
				} else {
					return false;
				}
			}

			@Override
			NavigableSet<K> keys() {
				return delegate.keys();
			}

			@Override
			void release(V value) {
				delegate.release(value);
				semaphore.release();
			}
		}

		public static class Evicting<K, V> extends Decorating<K, V> {

			private class Entry {

				private final K key;
				private final V value;
				private final long created;
				private long lastAccessed;
				private int counter;

				Entry(K key, V value) {
					this.key = Require.nonNull(key);
					this.value = Require.nonNull(value);
					this.created = System.currentTimeMillis();
					this.lastAccessed = created;
					this.counter = 1;
				}

				long created() {
					return created;
				}

				boolean evict() {
					return Evicting.this.evict(key, value);
				}

				long lastAccessed() {
					return lastAccessed;
				}

				long leastAccessed() {
					return created * counter;
				}

				void touch() {
					counter++;
					lastAccessed = System.currentTimeMillis();
				}
			}

			public enum EvictStrategy {
				ANY(null), //
				LAST_ACCESSED(Evicting<?, ?>.Entry::lastAccessed), //
				LEAST_ACCESSED(Evicting<?, ?>.Entry::leastAccessed), //
				OLDEST(Evicting<?, ?>.Entry::created);

				private final ToLongFunction<Evicting<?, ?>.Entry> property;

				EvictStrategy(ToLongFunction<Evicting<?, ?>.Entry> property) {
					this.property = property;
				}
			}

			private final Access<K, V> delegate;
			private final EvictStrategy strategy;
			private final int minCapacity;
			private final int maxCapacity;
			private final long threshold;
			private final Map<V, Entry> entries;

			Evicting(Access<K, V> delegate, EvictStrategy strategy, int minCapacity, int maxCapacity, long threshold) {
				this.delegate = Require.nonNull(delegate);
				this.strategy = Require.nonNull(strategy);
				this.minCapacity = Require.that(minCapacity, minCapacity >= 0);
				this.maxCapacity = Require.that(maxCapacity, maxCapacity >= minCapacity);
				this.threshold = Require.that(threshold, threshold > 0);
				this.entries = Collections.synchronizedMap(new IdentityHashMap<>());
			}

			@Override
			V acquire(K key, TSupplier<? extends V> supplier, long timeoutInMillis) {
				return delegate.acquire(key, () -> newValue(key, supplier), timeoutInMillis);
			}

			@Override
			boolean evict(K key, V value) {
				entries.remove(value);
				return delegate.evict(key, value);
			}

			private void evictMatching() {
				ToLongFunction<Evicting<?, ?>.Entry> prop = strategy.property;
				if (entries.size() > minCapacity && prop != null) {
					List<Entry> copy = new ArrayList<>(entries.values());
					long now = System.currentTimeMillis();
					Predicate<Evicting<?, ?>.Entry> predicate = e -> now - prop.applyAsLong(e) > threshold;
					copy.stream().limit(copy.size() - minCapacity).filter(predicate).forEach(Entry::evict);
				}
			}

			private void evictRemaining() {
				if (entries.size() > maxCapacity) {
					List<Entry> copy = new ArrayList<>(entries.values());
					ToLongFunction<Evicting<?, ?>.Entry> prop = strategy.property;
					if (prop != null) {
						Collections.sort(copy, (e1, e2) -> Long.compareUnsigned(prop.applyAsLong(e1), prop.applyAsLong(e2)));
					}
					copy.stream().limit(copy.size() - maxCapacity).forEach(Entry::evict);
				}
			}

			@Override
			NavigableSet<K> keys() {
				return delegate.keys();
			}

			private V newValue(K key, TSupplier<? extends V> supplier) {
				V value = supplier.get();
				entries.put(value, new Entry(key, value));
				return value;
			}

			@Override
			void release(V value) {
				evictMatching();
				evictRemaining();
				Entry entry = entries.get(value);
				if (entry != null) {
					entry.touch();
					delegate.release(value);
				}
			}

			public Evicting<K, V> whenOlderThan(long duration, TimeUnit unit) {
				long thresholdInMillis = unit.toMillis(duration);
				return new Evicting<>(delegate, strategy, minCapacity, maxCapacity, thresholdInMillis);
			}

			public Evicting<K, V> withCapacity(int min, int max) {
				return new Evicting<>(delegate, strategy, min, max, threshold);
			}

			public Evicting<K, V> withMaximumCapacity(int capacity) {
				return new Evicting<>(delegate, strategy, minCapacity, capacity, threshold);
			}

			public Evicting<K, V> withMinimumCapacity(int capacity) {
				return new Evicting<>(delegate, strategy, capacity, maxCapacity, threshold);
			}
		}

		public static class Retrying<K, V> extends Decorating<K, V> {

			private final Access<K, V> delegate;
			private final int maxRetries;
			private final long timeoutInMillis;

			Retrying(Access<K, V> delegate, int maxRetries, long timeoutInMillis) {
				this.delegate = Require.nonNull(delegate);
				this.maxRetries = Require.that(maxRetries, maxRetries >= 0);
				this.timeoutInMillis = Require.that(timeoutInMillis, timeoutInMillis >= 0);
			}

			@Override
			V acquire(K key, TSupplier<? extends V> supplier, long ignoredTimeout) {
				final long started = System.currentTimeMillis();
				V value = null;
				int tryCount = 0;
				long waitInMillis = Long.MAX_VALUE;
				do {
					tryCount++;
					waitInMillis = timeoutInMillis + started - System.currentTimeMillis();
					value = delegate.acquire(key, supplier, waitInMillis);
				} while (value == null && tryCount <= maxRetries && waitInMillis > 0);
				return value;
			}

			@Override
			boolean evict(K key, V value) {
				return delegate.evict(key, value);
			}

			@Override
			NavigableSet<K> keys() {
				return delegate.keys();
			}

			@Override
			void release(V value) {
				delegate.release(value);
			}

			public Retrying<K, V> times(int maxRetries) {
				return new Retrying<>(delegate, maxRetries, timeoutInMillis);
			}

			public Retrying<K, V> untilTimeout(long duration, TimeUnit unit) {
				return new Retrying<>(delegate, maxRetries, unit.toMillis(duration));
			}
		}
	}

	@FunctionalInterface
	interface Factory<K, V> {

		V newInstance(K key) throws Exception;

		default TSupplier<V> supplierFor(K key) {
			return () -> newInstance(key);
		}
	}

	@FunctionalInterface
	interface KeyLookupStrategy {

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

	class Pool<V> implements Cache<Void, V> {

		private final LongFunction<V> acquirer;
		private final Consumer<V> releaser;

		<K> Pool(ReadThrough<K, V> delegate, K key) {
			Require.nonNullElements(delegate, key);
			this.acquirer = timeout -> delegate.acquire(key, timeout);
			this.releaser = delegate::release;
		}

		public V acquire() {
			return acquirer.apply(Long.MAX_VALUE);
		}

		public V acquire(long timeoutInMillis) {
			return acquirer.apply(timeoutInMillis);
		}

		@Override
		public void release(V value) {
			releaser.accept(value);
		}
	}

	class ReadThrough<K, V> implements Cache<K, V> {

		private final Aside<K, V> delegate;
		private final Factory<? super K, ? extends V> factory;

		ReadThrough(Aside<K, V> delegate, Factory<? super K, ? extends V> factory) {
			this.delegate = Require.nonNull(delegate);
			this.factory = Require.nonNull(factory);
		}

		public V acquire(K key) {
			return delegate.acquire(key, factory.supplierFor(key), Long.MAX_VALUE);
		}

		public V acquire(K key, long timeoutInMillis) {
			return delegate.acquire(key, factory.supplierFor(key), timeoutInMillis);
		}

		public Pool<V> pooledBy(K key) {
			return new Pool<>(this, key);
		}

		@Override
		public void release(V value) {
			delegate.release(value);
		}
	}

	static <K extends Comparable<K>, V> Exclusive<K, V> exclusive(Function<? super V, ? extends K> keyedBy) {
		return new Exclusive<>(Comparable::compareTo, keyedBy);
	}

	static <K, V> Exclusive<K, V> exclusive(Function<? super V, ? extends K> keyedBy, Comparator<K> keyComparator) {
		return new Exclusive<>(keyComparator, keyedBy);
	}

	static <K extends Comparable<K>, V> Shared<K, V> shared() {
		return new Shared<>(Comparable::compareTo);
	}

	static <K, V> Shared<K, V> shared(Comparator<K> keyComparator) {
		return new Shared<>(keyComparator);
	}

	static <K, V> Shared<K, V> sharedOnEqualKey() {
		return new Shared<>((k1, k2) -> k1.equals(k2) ? 0 : (k1.hashCode() - k2.hashCode()) | 1);
	}

	void release(V value);
}
