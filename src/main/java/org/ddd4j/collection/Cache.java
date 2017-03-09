package org.ddd4j.collection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

import org.ddd4j.Throwing;
import org.ddd4j.Throwing.Producer;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.collection.Cache.Access.Exclusive;
import org.ddd4j.collection.Cache.Access.Shared;
import org.ddd4j.collection.Cache.Decorating.Blocking;
import org.ddd4j.collection.Cache.Decorating.Evicting;
import org.ddd4j.collection.Cache.Decorating.Evicting.EvictStrategy;
import org.ddd4j.collection.Cache.Decorating.Listening;
import org.ddd4j.collection.Cache.Decorating.Retrying;
import org.ddd4j.collection.Cache.Decorating.Wrapped;
import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Tpl;

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
			V acquire(K key, Producer<Tpl<K, V>> factory, long timeoutInMillis) {
				V value = null;
				Queue<V> queue = pool.get(key);
				if (queue != null) {
					value = queue.poll();
					if (queue.isEmpty() && pool.remove(key, queue)) {
						unusedQueues.offer(queue);
					}
				}
				return value != null ? value : factory.get().getRight();
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
				return queue != null && queue.remove(value);
			}

			@Override
			void evictAll(K key, Disposer<? super K, ? super V> disposer) {
				Queue<V> queue = pool.remove(key);
				if (queue != null) {
					Exception[] exceptions = queue.stream()
							.map(v -> disposer.disposeAndReturn(key, v))
							.filter(Objects::nonNull)
							.toArray(Exception[]::new);
					queue.clear();
					unusedQueues.offer(queue);
					Arrays.stream(exceptions).forEach(Throwing::unchecked);
				}
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

			private final ConcurrentNavigableMap<K, Future<? extends V>> singletons;
			private final ConcurrentMap<V, Future<? extends V>> futures;

			Shared(Comparator<? super K> comparator) {
				this.singletons = new ConcurrentSkipListMap<>(comparator);
				this.futures = new ConcurrentHashMap<>();
			}

			@Override
			V acquire(K key, Producer<Tpl<K, V>> factory, long timeoutInMillis) {
				Future<? extends V> f = singletons.get(key);
				if (f == null) {
					FutureTask<? extends V> task = new FutureTask<>(factory.map(Tpl::getRight));
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
				Future<? extends V> future = futures.remove(value);
				return future != null ? singletons.remove(key, future) : false;
			}

			@Override
			void evictAll(K key, Disposer<? super K, ? super V> disposer) {
				try {
					V value = singletons.get(key).get();
					if (evict(key, value)) {
						disposer.dispose(key, value);
					}
				} catch (ExecutionException e) {
				} catch (Exception e) {
					Throwing.unchecked(e);
				}
			}

			@Override
			NavigableSet<K> keys() {
				return singletons.keySet();
			}

			@Override
			void release(V value) {
			}
		}

		abstract V acquire(K key, Producer<Tpl<K, V>> factory, long timeoutInMillis);

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

		abstract void evictAll(K key, Disposer<? super K, ? super V> disposer);

		abstract NavigableSet<K> keys();

		public Pool<V> lookupRandomValues(Producer<? extends V> factory) {
			Require.nonNull(factory);
			return lookupValues(KeyLookup.RANDOM).withFactory(nil -> factory.get()).pooledBy(null);
		}

		public Aside<K, V> lookupValues(KeyLookup keyLookup) {
			return lookupValues(keyLookup, UnaryOperator.identity());
		}

		public Aside<K, V> lookupValues(KeyLookup keyLookup, UnaryOperator<K> keyAdapter) {
			return new Aside<>(this, keyLookup, keyAdapter);
		}

		public Aside<K, V> lookupValuesWithEqualKeys() {
			return lookupValues(KeyLookup.EQUALS);
		}

		public Listening<K, V> on() {
			return new Listening<>(this);
		}

		abstract void release(V value);

		public Retrying<K, V> retry() {
			return new Retrying<>(this, 0, Long.MAX_VALUE);
		}

		<T> Access<K, T> wrapEntries(Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper) {
			return new Wrapped<>(this, wrapper, unwrapper);
		}
	}

	class Aside<K, V> implements Cache<K, V> {

		private final Access<K, V> delegate;
		private final KeyLookup keyLookup;
		private final UnaryOperator<K> keyAdapter;

		Aside(Access<K, V> delegate, KeyLookup keyLookup, UnaryOperator<K> keyAdapter) {
			this.delegate = Require.nonNull(delegate);
			this.keyLookup = Require.nonNull(keyLookup);
			this.keyAdapter = Require.nonNull(keyAdapter);
		}

		public V acquire(K key, Factory<? super K, ? extends V> factory) {
			return acquire(key, factory, Long.MAX_VALUE);
		}

		public V acquire(K key, Factory<? super K, ? extends V> factory, long timeoutInMillis) {
			K effectiveKey = keyLookup.findOrDefault(delegate.keys(), key);
			return delegate.acquire(effectiveKey, () -> Tpl.of(key, factory.newInstance(key)), timeoutInMillis);
		}

		@Override
		public void evictAll(Disposer<K, V> disposer) {
			new HashSet<>(delegate.keys()).forEach(k -> delegate.evictAll(k, disposer));
		}

		public void evictAll(K key, Disposer<? super K, ? super V> disposer) {
			delegate.evictAll(key, disposer);
		}

		@Override
		public void release(V value) {
			delegate.release(value);
		}

		public ReadThrough<K, V> withFactory(Factory<? super K, ? extends V> factory) {
			return new ReadThrough<>(this, factory, (k, v) -> {
			});
		}

		public ReadThrough<K, V> withFactory(Factory<? super K, ? extends V> factory, Disposer<? super K, ? super V> disposer) {
			return new ReadThrough<>(this, factory, disposer);
		}

		@Override
		public <T> Aside<K, T> wrapEntries(Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper) {
			return new Aside<>(delegate.wrapEntries(wrapper, unwrapper), keyLookup, keyAdapter);
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
			V acquire(K key, Producer<Tpl<K, V>> factory, long timeoutInMillis) {
				final long started = System.currentTimeMillis();
				V value = null;
				long waitInMillis;
				do {
					waitInMillis = timeoutInMillis + started - System.currentTimeMillis();
					try {
						if (semaphore.tryAcquire(waitInMillis, TimeUnit.MILLISECONDS)) {
							waitInMillis = timeoutInMillis + started - System.currentTimeMillis();
							value = delegate.acquire(key, factory, waitInMillis);
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
			void evictAll(K key, Disposer<? super K, ? super V> disposer) {
				delegate.evictAll(key, disposer.andThen((k, v) -> semaphore.release()));
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
				private long lastAcquired;
				private long lastReleased;
				private int counter;
				private boolean evictable;

				Entry(K key, V value) {
					this.key = Require.nonNull(key);
					this.value = Require.nonNull(value);
					this.created = System.currentTimeMillis();
					this.lastAcquired = created;
					this.lastReleased = created;
					this.counter = 1;
					this.evictable = true;
				}

				V acquire() {
					evictable = false;
					lastAcquired = System.currentTimeMillis();
					return value;
				}

				long created() {
					return created;
				}

				boolean evict() {
					return Evicting.this.evict(key, value);
				}

				boolean isEvictable() {
					return evictable;
				}

				long lastAcquired() {
					return lastAcquired;
				}

				long lastReleased() {
					return lastReleased;
				}

				long leastAccessed() {
					return created * counter;
				}

				void release() {
					evictable = true;
					lastReleased = System.currentTimeMillis();
					counter++;
					delegate.release(value);
				}
			}

			public enum EvictStrategy {
				ANY(null), //
				LAST_ACQUIRED(Evicting<?, ?>.Entry::lastAcquired), //
				LAST_RELEASED(Evicting<?, ?>.Entry::lastReleased), //
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
			V acquire(K key, Producer<Tpl<K, V>> factory, long timeoutInMillis) {
				return entries.get(delegate.acquire(key, () -> registerEntry(factory), timeoutInMillis)).acquire();
			}

			@Override
			boolean evict(K key, V value) {
				entries.remove(value);
				return delegate.evict(key, value);
			}

			@Override
			void evictAll(K key, Disposer<? super K, ? super V> disposer) {
				delegate.evictAll(key, disposer.andThen(entries::remove));
			}

			private void evictOverCapacity() {
				if (entries.size() > maxCapacity) {
					List<Entry> copy = new ArrayList<>(entries.values());
					ToLongFunction<Evicting<?, ?>.Entry> prop = strategy.property;
					if (prop != null) {
						copy.sort((e1, e2) -> Long.compareUnsigned(prop.applyAsLong(e1), prop.applyAsLong(e2)));
					}
					Predicate<Entry> predicate = Entry::isEvictable;
					copy.stream().filter(predicate).limit(copy.size() - maxCapacity).forEach(Entry::evict);
				}
			}

			private void evictOverThreshold() {
				ToLongFunction<Evicting<?, ?>.Entry> prop = strategy.property;
				if (entries.size() > minCapacity && prop != null) {
					List<Entry> copy = new ArrayList<>(entries.values());
					long now = System.currentTimeMillis();
					Predicate<Entry> predicate = Entry::isEvictable;
					predicate = predicate.and(e -> now - prop.applyAsLong(e) > threshold);
					copy.stream().filter(predicate).limit(copy.size() - minCapacity).forEach(Entry::evict);
				}
			}

			@Override
			NavigableSet<K> keys() {
				return delegate.keys();
			}

			private Tpl<K, V> registerEntry(Producer<Tpl<K, V>> factory) {
				Tpl<K, V> tpl = factory.get();
				entries.put(tpl.getRight(), new Entry(tpl.getLeft(), tpl.getRight()));
				return tpl;
			}

			@Override
			void release(V value) {
				Entry entry = entries.get(value);
				if (entry != null) {
					entry.release();
				}
				evictOverThreshold();
				evictOverCapacity();
			}

			public Evicting<K, V> whenThresholdReached(long duration, TimeUnit unit) {
				Require.that(strategy != EvictStrategy.ANY);
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

		public static class Listening<K, V> extends Decorating<K, V> {

			private final Access<K, V> delegate;
			private final List<Listener<? super V>> listeners;

			Listening(Access<K, V> delegate) {
				this.delegate = Require.nonNull(delegate);
				this.listeners = Collections.emptyList();
			}

			private Listening(Listening<K, V> copy, Listener<? super V> listener) {
				this.delegate = copy.delegate;
				this.listeners = new ArrayList<>(copy.listeners.size() + 1);
				this.listeners.addAll(copy.listeners);
				this.listeners.add(Require.nonNull(listener));
			}

			@Override
			V acquire(K key, Producer<Tpl<K, V>> factory, long timeoutInMillis) {
				V value = delegate.acquire(key, factory, timeoutInMillis);
				listeners.forEach(l -> l.onAcquired(value));
				return value;
			}

			public Listening<K, V> acquired(Consumer<? super V> listener) {
				Require.nonNull(listener);
				return lifecycle(new Listener<V>() {

					@Override
					public void onAcquired(V value) {
						listener.accept(value);
					}
				});
			}

			@Override
			boolean evict(K key, V value) {
				if (delegate.evict(key, value)) {
					listeners.forEach(l -> l.onEvicted(value));
					return true;
				} else {
					return false;
				}
			}

			@Override
			void evictAll(K key, Disposer<? super K, ? super V> disposer) {
				delegate.evictAll(key, disposer);
			}

			public Listening<K, V> evicted(Consumer<? super V> listener) {
				Require.nonNull(listener);
				return lifecycle(new Listener<V>() {

					@Override
					public void onEvicted(V value) {
						listener.accept(value);
					}
				});
			}

			@Override
			NavigableSet<K> keys() {
				return delegate.keys();
			}

			public Listening<K, V> lifecycle(Listener<? super V> listener) {
				return new Listening<>(this, listener);
			}

			@Override
			void release(V value) {
				listeners.forEach(l -> l.onReleased(value));
				delegate.release(value);
			}

			public Listening<K, V> released(Consumer<? super V> listener) {
				Require.nonNull(listener);
				return lifecycle(new Listener<V>() {

					@Override
					public void onReleased(V value) {
						listener.accept(value);
					}
				});
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
			V acquire(K key, Producer<Tpl<K, V>> factory, long ignoredTimeout) {
				final long started = System.currentTimeMillis();
				V value = null;
				int tryCount = 0;
				long waitInMillis = Long.MAX_VALUE;
				do {
					tryCount++;
					waitInMillis = timeoutInMillis + started - System.currentTimeMillis();
					value = delegate.acquire(key, factory, waitInMillis);
				} while (value == null && tryCount <= maxRetries && waitInMillis > 0);
				return value;
			}

			@Override
			boolean evict(K key, V value) {
				return delegate.evict(key, value);
			}

			@Override
			void evictAll(K key, Disposer<? super K, ? super V> disposer) {
				delegate.evictAll(key, disposer);
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

		public static class Wrapped<K, V, T> extends Decorating<K, T> {

			private final Access<K, V> delegate;
			private final Function<? super V, ? extends T> wrapper;
			private final Function<? super T, ? extends V> unwrapper;

			Wrapped(Access<K, V> delegate, Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper) {
				this.delegate = Require.nonNull(delegate);
				this.wrapper = Require.nonNull(wrapper);
				this.unwrapper = Require.nonNull(unwrapper);
			}

			@Override
			T acquire(K key, Producer<Tpl<K, T>> factory, long timeoutInMillis) {
				return wrapper.apply(delegate.acquire(key, factory.map(tpl -> tpl.mapRight(unwrapper)), timeoutInMillis));
			}

			@Override
			boolean evict(K key, T value) {
				return delegate.evict(key, unwrapper.apply(value));
			}

			@Override
			void evictAll(K key, Disposer<? super K, ? super T> disposer) {
				delegate.evictAll(key, disposer.with(wrapper));
			}

			@Override
			NavigableSet<K> keys() {
				return delegate.keys();
			}

			@Override
			void release(T value) {
				delegate.release(unwrapper.apply(value));
			}
		}
	}

	@FunctionalInterface
	interface Disposer<K, V> {

		default Disposer<K, V> andThen(Disposer<? super K, ? super V> then) {
			return (k, v) -> {
				try {
					this.dispose(k, v);
				} finally {
					then.dispose(k, v);
				}
			};
		}

		void dispose(K key, V value) throws Exception;

		default Exception disposeAndReturn(K key, V value) {
			try {
				dispose(key, value);
				return null;
			} catch (Exception e) {
				return e;
			}
		}

		default <T> Disposer<K, T> with(Function<? super T, ? extends V> wrapper) {
			return (k, t) -> dispose(k, wrapper.apply(t));
		}
	}

	@FunctionalInterface
	interface Factory<K, V> {

		default <T> Factory<K, T> andThen(Function<? super V, ? extends T> mapper) {
			return k -> mapper.apply(newInstance(k));
		}

		default Callable<V> callableFor(K key) {
			return () -> newInstance(key);
		}

		V newInstance(K key) throws Exception;
	}

	interface KeyLookup {

		KeyLookup EQUALS = KeyLookup::equals;
		KeyLookup FIRST = KeyLookup::first;
		KeyLookup LAST = KeyLookup::last;
		KeyLookup CEILING = NavigableSet::ceiling;
		KeyLookup HIGHER = NavigableSet::higher;
		KeyLookup FLOOR = NavigableSet::floor;
		KeyLookup LOWER = NavigableSet::lower;
		KeyLookup RANDOM = KeyLookup::random;

		static <K> K equals(NavigableSet<K> keys, K key) {
			return keys.contains(key) ? key : null;
		}

		static <K> K first(NavigableSet<K> keys, K key) {
			return keys.isEmpty() ? null : keys.first();
		}

		static <K> K last(NavigableSet<K> keys, K key) {
			return keys.isEmpty() ? null : keys.last();
		}

		static <K> K random(NavigableSet<K> keys, K key) {
			return keys.isEmpty() ? null : new ArrayList<>(keys).get(ThreadLocalRandom.current().nextInt(keys.size()));
		}

		<K> K apply(NavigableSet<K> keys, K key);

		default <K> K findOrDefault(NavigableSet<K> keys, K key) {
			K found = apply(keys, key);
			return found != null ? found : key;
		}
	}

	interface Listener<V> {

		default void onAcquired(V value) {
		}

		default void onEvicted(V value) {
		}

		default void onReleased(V value) {
		}
	}

	class Pool<V> implements Cache<Void, V> {

		private class Holder<K> {

			private final ReadThrough<K, V> delegate;
			private final K key;

			Holder(ReadThrough<K, V> delegate, K key) {
				this.delegate = Require.nonNull(delegate);
				this.key = key;
			}

			V acquire() {
				return delegate.acquire(key);
			}

			V acquire(long timeoutInMillis) {
				return delegate.acquire(key, timeoutInMillis);
			}

			void evictAll(TConsumer<V> disposer) {
				delegate.evictAll((k, v) -> disposer.accept(v));
			}

			void release(V value) {
				delegate.release(value);
			}

			<T> Pool<T> wrapEntries(Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper) {
				return new Pool<>(delegate.wrapEntries(wrapper, unwrapper), key);
			}
		}

		private final Holder<?> holder;

		<K> Pool(ReadThrough<K, V> delegate, K key) {
			holder = new Holder<>(delegate, key);
		}

		public V acquire() {
			return holder.acquire();
		}

		public V acquire(long timeoutInMillis) {
			return holder.acquire(timeoutInMillis);
		}

		@Override
		public void evictAll(Disposer<Void, V> disposer) {
			holder.evictAll(v -> disposer.dispose(null, v));
		}

		public void evictAll(TConsumer<V> disposer) {
			holder.evictAll(disposer);
		}

		@Override
		public void release(V value) {
			holder.release(value);
		}

		@Override
		public <T> Pool<T> wrapEntries(Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper) {
			return holder.wrapEntries(wrapper, unwrapper);
		}
	}

	class ReadThrough<K, V> implements Cache<K, V> {

		private final Aside<K, V> delegate;
		private final Factory<? super K, ? extends V> factory;
		private final Disposer<? super K, ? super V> disposer;

		ReadThrough(Aside<K, V> delegate, Factory<? super K, ? extends V> factory, Disposer<? super K, ? super V> disposer) {
			this.delegate = Require.nonNull(delegate);
			this.factory = Require.nonNull(factory);
			this.disposer = Require.nonNull(disposer);
		}

		public V acquire(K key) {
			return delegate.acquire(key, factory, Long.MAX_VALUE);
		}

		public V acquire(K key, long timeoutInMillis) {
			return delegate.acquire(key, factory, timeoutInMillis);
		}

		public void evictAll() {
			evictAll((k, v) -> {
			});
		}

		@Override
		public void evictAll(Disposer<K, V> disposer) {
			delegate.evictAll(disposer.andThen(this.disposer));
		}

		public void evictAll(K key, Disposer<? super K, ? super V> disposer) {
			delegate.evictAll(key, disposer);
		}

		public Pool<V> pooledBy(K key) {
			return new Pool<>(this, key);
		}

		@Override
		public void release(V value) {
			delegate.release(value);
		}

		@Override
		public <T> ReadThrough<K, T> wrapEntries(Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper) {
			return new ReadThrough<>(delegate.wrapEntries(wrapper, unwrapper), factory.andThen(wrapper), disposer.with(unwrapper));
		}
	}

	static <K extends Comparable<K>, V> Exclusive<K, V> exclusive(Function<? super V, ? extends K> keyedBy) {
		return new Exclusive<>(Comparable::compareTo, keyedBy);
	}

	static <K, V> Exclusive<K, V> exclusive(Function<? super V, ? extends K> keyedBy, Comparator<K> keyComparator) {
		return new Exclusive<>(keyComparator, keyedBy);
	}

	static <V> Exclusive<Integer, V> exclusiveOnIdentity() {
		return new Exclusive<>(Comparable::compareTo, Object::hashCode);
	}

	static <K extends Comparable<K>, V> Shared<K, V> shared() {
		return new Shared<>(Comparable::compareTo);
	}

	static <K, V> Shared<K, V> shared(Comparator<K> keyComparator) {
		return new Shared<>(keyComparator);
	}

	static <K, V> Aside<K, V> sharedOnEqualKey() {
		return new Shared<K, V>((k1, k2) -> k1.equals(k2) ? 0 : (k1.hashCode() - k2.hashCode()) | 1).lookupValuesWithEqualKeys();
	}

	void evictAll(Disposer<K, V> disposer);

	void release(V value);

	<T> Cache<K, T> wrapEntries(Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper);
}
