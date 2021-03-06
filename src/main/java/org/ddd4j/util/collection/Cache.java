package org.ddd4j.util.collection;

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
import java.util.Optional;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing;
import org.ddd4j.util.Throwing.Producer;
import org.ddd4j.util.Throwing.TConsumer;
import org.ddd4j.util.collection.Cache.Access.Exclusive;
import org.ddd4j.util.collection.Cache.Access.Shared;
import org.ddd4j.util.collection.Cache.Decorating.Blocking;
import org.ddd4j.util.collection.Cache.Decorating.Evicting;
import org.ddd4j.util.collection.Cache.Decorating.Listening;
import org.ddd4j.util.collection.Cache.Decorating.Retrying;
import org.ddd4j.value.config.ConfKey;

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
			V acquire(K key, Producer<Entry<K, V>> factory, long timeoutInMillis) {
				V value = null;
				Queue<V> queue = pool.get(key);
				if (queue != null) {
					value = queue.poll();
					if (queue.isEmpty() && pool.remove(key, queue)) {
						unusedQueues.offer(queue);
					}
				}
				return value != null ? value : factory.get().getValue();
			}

			@Override
			boolean contains(K key) {
				return pool.containsKey(key);
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
				this.singletons = new ConcurrentSkipListMap<>(Require.nonNull(comparator));
				this.futures = new ConcurrentHashMap<>();
			}

			@Override
			V acquire(K key, Producer<Entry<K, V>> factory, long timeoutInMillis) {
				Future<? extends V> f = singletons.get(key);
				if (f == null) {
					FutureTask<? extends V> task = new FutureTask<>(factory.map(Entry::getValue));
					f = singletons.putIfAbsent(key, task);
					if (f == null) {
						f = task;
						task.run();
						try {
							futures.put(task.get(), task);
						} catch (Exception e) {
							Throwing.unchecked(e);
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
			boolean contains(K key) {
				return singletons.containsKey(key);
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

		private static class Wrapped<K, V, T> extends Access<K, T> {

			private final Access<K, V> delegate;
			private final Function<? super V, ? extends T> wrapper;
			private final Function<? super T, ? extends V> unwrapper;

			Wrapped(Access<K, V> delegate, Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper) {
				this.delegate = Require.nonNull(delegate);
				this.wrapper = Require.nonNull(wrapper);
				this.unwrapper = Require.nonNull(unwrapper);
			}

			@Override
			T acquire(K key, Producer<Entry<K, T>> factory, long timeoutInMillis) {
				return wrapper.apply(delegate.acquire(key, factory.map(tpl -> tpl.mapValue(unwrapper)), timeoutInMillis));
			}

			@Override
			boolean contains(K key) {
				return delegate.contains(key);
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

		abstract V acquire(K key, Producer<Entry<K, V>> factory, long timeoutInMillis);

		public Access<K, V> blockOn(int capacity, boolean fair) {
			return new Blocking<>(this, capacity, fair);
		}

		abstract boolean contains(K key);

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

		private static <K, V> void ignore(K key, V value) {
		}

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
			K effectiveKey = keyLookup.find(delegate.keys(), key).orElse(keyAdapter.apply(key));
			return delegate.acquire(effectiveKey, () -> new Entry<>(key, factory.create(key)), timeoutInMillis);
		}

		public boolean contains(K key) {
			return delegate.contains(key);
		}

		@Override
		public void evictAll(Disposer<K, V> disposer) {
			new HashSet<>(delegate.keys()).forEach(k -> delegate.evictAll(k, disposer));
		}

		public void evictAll(K key) {
			delegate.evictAll(key, Aside::ignore);
		}

		public void evictAll(K key, Disposer<? super K, ? super V> disposer) {
			delegate.evictAll(key, disposer);
		}

		public void release(V value) {
			delegate.release(value);
		}

		public ReadThrough<K, V> withFactory(Factory<? super K, ? extends V> factory) {
			return new ReadThrough<>(this, factory, Aside::ignore);
		}

		public ReadThrough<K, V> withFactory(Factory<? super K, ? extends V> factory, Disposer<? super K, ? super V> disposer) {
			return new ReadThrough<>(this, factory, disposer);
		}

		@Override
		public <T> Aside<K, T> wrapEntries(Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper) {
			return new Aside<>(delegate.wrapEntries(wrapper, unwrapper), keyLookup, keyAdapter);
		}

		public WriteThrough<K, V> writeThrough(Reader<? super K, ? extends V> reader, Writer<? super K, V> writer) {
			return new WriteThrough<>(this, reader, writer);
		}
	}

	abstract class Decorating<K, V> extends Access<K, V> {

		public static class Blocking<K, V> extends Decorating<K, V> {

			private final Semaphore semaphore;

			public Blocking(Access<K, V> delegate, int capacity, boolean fair) {
				super(delegate);
				this.semaphore = new Semaphore(capacity, fair);
			}

			@Override
			V acquire(K key, Producer<Entry<K, V>> factory, long timeoutInMillis) {
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
			void release(V value) {
				delegate.release(value);
				semaphore.release();
			}
		}

		public static class Evicting<K, V> extends Decorating<K, V> {

			private class EvictEntry {

				private final K key;
				private final V value;
				private final long created;
				private long lastAcquired;
				private long lastReleased;
				private int counter;
				private boolean evictable;

				EvictEntry(K key, V value) {
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

			private final EvictStrategy strategy;
			private final int minCapacity;
			private final int maxCapacity;
			private final long threshold;
			private final Map<V, EvictEntry> entries;

			Evicting(Access<K, V> delegate, EvictStrategy strategy, int minCapacity, int maxCapacity, long threshold) {
				super(delegate);
				this.strategy = Require.nonNull(strategy);
				this.minCapacity = Require.that(minCapacity, minCapacity >= 0);
				this.maxCapacity = Require.that(maxCapacity, maxCapacity >= minCapacity);
				this.threshold = Require.that(threshold, threshold > 0);
				this.entries = Collections.synchronizedMap(new IdentityHashMap<>());
			}

			@Override
			V acquire(K key, Producer<Entry<K, V>> factory, long timeoutInMillis) {
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
					List<EvictEntry> copy = new ArrayList<>(entries.values());
					ToLongFunction<Evicting<?, ?>.EvictEntry> prop = strategy.property;
					if (prop != null) {
						copy.sort((e1, e2) -> Long.compareUnsigned(prop.applyAsLong(e1), prop.applyAsLong(e2)));
					}
					Predicate<EvictEntry> predicate = EvictEntry::isEvictable;
					copy.stream().filter(predicate).limit(copy.size() - maxCapacity).forEach(EvictEntry::evict);
				}
			}

			private void evictOverThreshold() {
				ToLongFunction<Evicting<?, ?>.EvictEntry> prop = strategy.property;
				if (entries.size() > minCapacity && prop != null) {
					List<EvictEntry> copy = new ArrayList<>(entries.values());
					long now = System.currentTimeMillis();
					Predicate<EvictEntry> predicate = EvictEntry::isEvictable;
					predicate = predicate.and(e -> now - prop.applyAsLong(e) > threshold);
					copy.stream().filter(predicate).limit(copy.size() - minCapacity).forEach(EvictEntry::evict);
				}
			}

			private Entry<K, V> registerEntry(Producer<Entry<K, V>> factory) {
				Entry<K, V> tpl = factory.get();
				entries.put(tpl.getValue(), new EvictEntry(tpl.getKey(), tpl.getValue()));
				return tpl;
			}

			@Override
			void release(V value) {
				EvictEntry entry = entries.get(value);
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

			private final List<Listener<? super V>> listeners;

			Listening(Access<K, V> delegate) {
				super(delegate);
				this.listeners = Collections.emptyList();
			}

			private Listening(Listening<K, V> copy, Listener<? super V> listener) {
				super(copy.delegate);
				this.listeners = new ArrayList<>(copy.listeners.size() + 1);
				this.listeners.addAll(copy.listeners);
				this.listeners.add(Require.nonNull(listener));
			}

			@Override
			V acquire(K key, Producer<Entry<K, V>> factory, long timeoutInMillis) {
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

			public Listening<K, V> evicted(Consumer<? super V> listener) {
				Require.nonNull(listener);
				return lifecycle(new Listener<V>() {

					@Override
					public void onEvicted(V value) {
						listener.accept(value);
					}
				});
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

			private final int maxRetries;
			private final long timeoutInMillis;

			Retrying(Access<K, V> delegate, int maxRetries, long timeoutInMillis) {
				super(delegate);
				this.maxRetries = Require.that(maxRetries, maxRetries >= 0);
				this.timeoutInMillis = Require.that(timeoutInMillis, timeoutInMillis >= 0);
			}

			@Override
			V acquire(K key, Producer<Entry<K, V>> factory, long ignoredTimeout) {
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

			public Retrying<K, V> times(int maxRetries) {
				return new Retrying<>(delegate, maxRetries, timeoutInMillis);
			}

			public Retrying<K, V> untilTimeout(long duration, TimeUnit unit) {
				return new Retrying<>(delegate, maxRetries, unit.toMillis(duration));
			}
		}

		protected final Access<K, V> delegate;

		protected Decorating(Access<K, V> delegate) {
			this.delegate = Require.nonNull(delegate);
		}

		@Override
		boolean contains(K key) {
			return delegate.contains(key);
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

	class Entry<K, V> {

		private final K key;
		private final V value;

		public Entry(K key, V value) {
			this.key = Require.nonNull(key);
			this.value = Require.nonNull(value);
		}

		public K getKey() {
			return key;
		}

		public V getValue() {
			return value;
		}

		<X> Entry<K, X> mapValue(Function<? super V, ? extends X> mapper) {
			return new Entry<>(key, mapper.apply(value));
		}
	}

	enum EvictStrategy {
		ANY(null), //
		LAST_ACQUIRED(Evicting<?, ?>.EvictEntry::lastAcquired), //
		LAST_RELEASED(Evicting<?, ?>.EvictEntry::lastReleased), //
		LEAST_ACCESSED(Evicting<?, ?>.EvictEntry::leastAccessed), //
		OLDEST(Evicting<?, ?>.EvictEntry::created);

		private final ToLongFunction<Evicting<?, ?>.EvictEntry> property;

		EvictStrategy(ToLongFunction<Evicting<?, ?>.EvictEntry> property) {
			this.property = property;
		}
	}

	@FunctionalInterface
	interface Factory<K, V> {

		default <T> Factory<K, T> andThen(Function<? super V, ? extends T> mapper) {
			return k -> mapper.apply(create(k));
		}

		default Supplier<V> asSupplier(K key) {
			return () -> {
				try {
					return create(key);
				} catch (Exception e) {
					return Throwing.unchecked(e);
				}
			};
		}

		V create(K key) throws Exception;
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

		default <K> Optional<K> find(NavigableSet<K> keys, K key) {
			return Optional.ofNullable(apply(keys, key));
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

		public void release(V value) {
			holder.release(value);
		}

		@Override
		public <T> Pool<T> wrapEntries(Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper) {
			return holder.wrapEntries(wrapper, unwrapper);
		}
	}

	@FunctionalInterface
	interface Reader<K, V> {

		default <T> Reader<K, T> andThen(Function<? super V, ? extends T> mapper) {
			return k -> mapper.apply(read(k));
		}

		V read(K key) throws Exception;
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
			return delegate.acquire(key, factory);
		}

		public V acquire(K key, long timeoutInMillis) {
			return delegate.acquire(key, factory, timeoutInMillis);
		}

		public boolean contains(K key) {
			return delegate.contains(key);
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

		public void release(V value) {
			delegate.release(value);
		}

		@Override
		public <T> ReadThrough<K, T> wrapEntries(Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper) {
			return new ReadThrough<>(delegate.wrapEntries(wrapper, unwrapper), factory.andThen(wrapper), disposer.with(unwrapper));
		}
	}

	@FunctionalInterface
	interface Writer<K, V> {

		default <T> Writer<K, T> andThen(Function<? super T, ? extends V> mapper) {
			return (k, n, o, u) -> updateIfNecessary(k, mapper.apply(n), o.map(mapper), u);
		}

		void updateIfNecessary(K key, V newValue, Optional<V> oldValue, Runnable cacheUpdater) throws Exception;
	}

	class WriteThrough<K, V> implements Cache<K, V> {

		private class Holder {

			V value;
		}

		private final Aside<K, V> delegate;
		private final Reader<? super K, ? extends V> reader;
		private final Writer<? super K, V> writer;

		WriteThrough(Aside<K, V> delegate, Reader<? super K, ? extends V> reader, Writer<? super K, V> writer) {
			this.delegate = Require.nonNull(delegate);
			this.reader = Require.nonNull(reader);
			this.writer = Require.nonNull(writer);
		}

		public boolean contains(K key) {
			return delegate.contains(key);
		}

		@Override
		public void evictAll(Disposer<K, V> disposer) {
			delegate.evictAll(disposer);
		}

		public V get(K key) {
			return get(key, Long.MAX_VALUE);
		}

		public V get(K key, long timeoutInMillis) {
			return delegate.acquire(key, reader::read, timeoutInMillis);
		}

		public V put(K key, V newValue) {
			Holder holder = new Holder();
			try {
				holder.value = get(key);
			} catch (Exception e) {
				holder.value = null;
			}
			try {
				writer.updateIfNecessary(key, newValue, Optional.ofNullable(holder.value), () -> {
					delegate.evictAll(key);
					delegate.acquire(key, k -> newValue);
					holder.value = newValue;
				});
			} catch (Exception e) {
				Throwing.unchecked(e);
			}
			return Require.nonNull(holder.value);
		}

		@Override
		public <T> WriteThrough<K, T> wrapEntries(Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper) {
			return new WriteThrough<>(delegate.wrapEntries(wrapper, unwrapper), reader.andThen(wrapper), writer.andThen(unwrapper));
		}
	}

	ConfKey<Integer> MAX_CAPACITY = ConfKey.ofInteger("maxCapacity", 1024);

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

	static <K, V> Aside<K, V> sharedOnEqualKey() {
		return sharedOnEqualKey(Function.identity());
	}

	static <K, V> Aside<K, V> sharedOnEqualKey(Function<Access<K, V>, Access<K, V>> configurer) {
		return configurer.apply(new Shared<K, V>((k1, k2) -> k1.equals(k2) ? 0 : (k1.hashCode() - k2.hashCode()) | 1))
				.lookupValuesWithEqualKeys();
	}

	default void evictAll() {
		evictAll(Aside::ignore);
	}

	void evictAll(Disposer<K, V> disposer);

	<T> Cache<K, T> wrapEntries(Function<? super V, ? extends T> wrapper, Function<? super T, ? extends V> unwrapper);
}
