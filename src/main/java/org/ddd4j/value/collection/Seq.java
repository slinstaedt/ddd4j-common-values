package org.ddd4j.value.collection;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@FunctionalInterface
public interface Seq<L> extends Iterable<L> {

	@FunctionalInterface
	interface Extender<E> {

		Seq<E> apply(Supplier<Stream<? extends E>> other);

		default Seq<E> array(E[] entries) {
			return array(entries, entries.length);
		}

		default Seq<E> array(E[] entries, int newLength) {
			entries = Arrays.copyOf(entries, newLength);
			return apply(Arrays.asList(entries)::stream);
		}

		default Seq<E> array(E[] entries, int from, int to) {
			entries = Arrays.copyOfRange(entries, from, to);
			return apply(Arrays.asList(entries)::stream);
		}

		default Seq<E> collection(Collection<? extends E> entries) {
			return apply(new ArrayList<>(entries)::stream);
		}

		default Seq<E> entry(E entry) {
			return apply(Collections.singleton(entry)::stream);
		}

		default Seq<E> iterable(Iterable<? extends E> iterable) {
			List<E> list = new ArrayList<>();
			iterable.forEach(list::add);
			return apply(list::stream);
		}

		default Seq<E> repeated(int repeat, E entry) {
			return apply(Collections.nCopies(repeat, entry)::stream);
		}

		default Seq<E> seq(Seq<? extends E> seq) {
			return apply(requireNonNull(seq)::stream);
		}
	}

	@FunctionalInterface
	interface Filter<E> {

		<X> Seq<X> apply(Function<Seq<E>, Stream<X>> filter);

		default <X> Seq<X> applyStream(Function<Stream<E>, Stream<X>> filter) {
			requireNonNull(filter);
			return apply(s -> filter.apply(s.stream()));
		}

		default Seq<E> by(Predicate<? super E> predicate) {
			requireNonNull(predicate);
			return applyStream(s -> s.filter(predicate));
		}

		default Seq<E> by(Supplier<Predicate<? super E>> predicateSupplier) {
			requireNonNull(predicateSupplier);
			return applyStream(s -> s.filter(predicateSupplier.get()));
		}

		default <X> Seq<X> byType(Class<? extends X> type) {
			requireNonNull(type);
			return applyStream(s -> s.filter(type::isInstance).map(type::cast));
		}

		default Seq<E> distinct() {
			return distinct(Function.identity());
		}

		default Seq<E> distinct(Function<? super E, ?> keyMapper) {
			requireNonNull(keyMapper);
			return by(() -> {
				Set<Object> visited = new HashSet<>();
				return e -> visited.add(keyMapper.apply(e));
			});
		}

		default Seq<E> limit(long count) {
			return applyStream(s -> s.limit(count));
		}

		default Seq<E> limitUntil(Predicate<? super E> predicate) {
			return limitWhile(predicate.negate());
		}

		default Seq<E> limitWhile(Predicate<? super E> predicate) {
			requireNonNull(predicate);
			return by(() -> {
				Ref<Boolean> filterOutcome = Ref.of(Boolean.TRUE);
				return e -> filterOutcome.updateAndGet(b -> predicate.test(e), b -> b);
			});
		}

		default Seq<E> nonNull() {
			return by(Objects::nonNull);
		}

		default Seq<E> skip(long count) {
			return applyStream(s -> s.skip(count));
		}

		default Seq<E> skipUntil(Predicate<? super E> predicate) {
			requireNonNull(predicate);
			return by(() -> {
				Ref<Boolean> filterOutcome = Ref.of(Boolean.FALSE);
				return e -> filterOutcome.updateAndGet(b -> predicate.test(e), b -> !b);
			});
		}

		default Seq<E> skipWhile(Predicate<? super E> predicate) {
			return skipUntil(predicate.negate());
		}

		default Seq<E> slice(long from, long to) {
			return applyStream(s -> s.skip(from).limit(to - from));
		}

		default <X> Seq<E> where(Function<Mapper<E>, X> mapper, Predicate<? super X> predicate) {
			requireNonNull(mapper);
			requireNonNull(predicate);
			return apply(s -> s.stream().filter(e -> predicate.test(mapper.apply(s.map()))));
		}
	}

	@FunctionalInterface
	interface Joiner<L> {

		<R> Seq<Tpl<L, R>> apply(Seq<R> other, BiPredicate<L, R> predicate,
				Function<Extender<Tpl<L, R>>, Seq<Tpl<L, R>>> appender);

		default <R> Seq<Tpl<L, R>> inner(Seq<R> other) {
			return inner(other, (l, r) -> true);
		}

		default <R> Seq<Tpl<L, R>> inner(Seq<R> other, BiPredicate<? super L, ? super R> predicate) {
			requireNonNull(predicate);
			return apply(other, (l, r) -> l != null && r != null && Objects.equals(l, r) && predicate.test(l, r));
		}

		default <R> Seq<Tpl<L, R>> left(Seq<R> other) {
			return left(other, (l, r) -> true);
		}

		default <R> Seq<Tpl<L, R>> left(Seq<R> other, BiPredicate<? super L, ? super R> predicate) {
			requireNonNull(predicate);
			return apply(other, (l, r) -> (r == null || Objects.equals(l, r)) && predicate.test(l, r));
		}

		default <R> Seq<Tpl<L, R>> outer(Seq<R> other) {
			return outer(other, (l, r) -> true);
		}

		default <R> Seq<Tpl<L, R>> outer(Seq<R> other, BiPredicate<? super L, ? super R> predicate) {
			requireNonNull(predicate);
			return apply(other, (l, r) -> (l == null || r == null || Objects.equals(l, r)) && predicate.test(l, r));
		}

		default <R> Seq<Tpl<L, R>> right(Seq<R> other) {
			return right(other, (l, r) -> true);
		}

		default <R> Seq<Tpl<L, R>> right(Seq<R> other, BiPredicate<? super L, ? super R> predicate) {
			requireNonNull(predicate);
			return apply(other, (l, r) -> (l == null || Objects.equals(l, r)) && predicate.test(l, r));
		}
	}

	@FunctionalInterface
	interface Mapper<E> {

		<X> Seq<X> apply(Function<Seq<E>, Seq<X>> mapper);

		default Seq<Tpl<E, E>> consecutivePairwise() {
			return to(() -> {
				Ref<E> last = Ref.create();
				return e -> last.update(t -> e);
			}).filter().skip(1);
		}

		default <X> Seq<X> flat(Function<? super E, ? extends Seq<? extends X>> mapper) {
			return flatStream(mapper.andThen(Seq::stream));
		}

		default <X> Seq<X> flatArray(Function<? super E, X[]> mapper) {
			return flatStream(mapper.andThen(Stream::of));
		}

		default <X> Seq<X> flatCollection(Function<? super E, Collection<? extends X>> mapper) {
			return flatStream(mapper.andThen(Collection::stream));
		}

		default <X> Seq<X> flatStream(Function<? super E, Stream<? extends X>> mapper) {
			requireNonNull(mapper);
			return apply(s -> () -> s.stream().flatMap(mapper));
		}

		default <K> Seq<Tpl<K, Seq<E>>> grouped(Function<? super E, K> mapper) {
			requireNonNull(mapper);
			return apply(s -> s.map().to(mapper).filter().distinct().map()
					.to(k -> Tpl.of(k, s.filter().by(e -> Objects.equals(k, mapper.apply(e))))));
		}

		default Seq<Seq<E>> partition(long partitionSize) {
			return apply(s -> {
				long index = 0L;
				long size = s.size();
				List<Seq<E>> partitions = new ArrayList<>((int) (size / partitionSize) + 1);
				for (int i = 0; i < size; i++) {
					partitions.add(s.filter().slice(index, index += partitionSize));
				}
				return partitions::stream;
			});
		}

		default <P> Seq<Tpl<E, P>> project(Function<? super E, P> mapper) {
			requireNonNull(mapper);
			return to(e -> Tpl.of(e, mapper.apply(e)));
		}

		default Seq<E> recursively(Function<? super E, Seq<E>> mapper) {
			requireNonNull(mapper);
			return apply(s -> s.append().seq(flat(mapper).map().recursively(mapper)));
		}

		default Seq<E> recursivelyArray(Function<? super E, E[]> mapper) {
			requireNonNull(mapper);
			return apply(s -> s.append().seq(flatArray(mapper).map().recursivelyArray(mapper)));
		}

		default Seq<E> recursivelyCollection(Function<? super E, Collection<? extends E>> mapper) {
			requireNonNull(mapper);
			return apply(s -> s.append().seq(flatCollection(mapper).map().recursivelyCollection(mapper)));
		}

		default Seq<E> recursivelyStream(Function<? super E, Stream<? extends E>> mapper) {
			requireNonNull(mapper);
			return apply(s -> s.append().seq(flatStream(mapper).map().recursivelyStream(mapper)));
		}

		default <X> Seq<X> to(Function<? super E, ? extends X> mapper) {
			requireNonNull(mapper);
			return apply(s -> () -> s.stream().map(mapper));
		}

		default <X> Seq<X> to(Supplier<Function<? super E, ? extends X>> mapperSupplier) {
			requireNonNull(mapperSupplier);
			return apply(s -> () -> s.stream().map(mapperSupplier.get()));
		}

		default Seq<Tpl<E, Long>> zipWithIndex() {
			return to(() -> {
				Ref<Long> index = Ref.of(0L);
				return e -> Tpl.of(e, index.getAndUpdate(t -> t++));
			});
		}
	}

	static <E> Seq<E> concat(Supplier<? extends Stream<? extends E>> a, Supplier<? extends Stream<? extends E>> b) {
		if (Seq.of(a).isEmpty()) {
			return Seq.from(b);
		} else if (Seq.of(b).isEmpty()) {
			return Seq.from(a);
		} else {
			return () -> Stream.concat(a.get(), b.get());
		}
	}

	static <E> Seq<E> empty() {
		return Stream::empty;
	}

	@SuppressWarnings("unchecked")
	static <E> Seq<E> from(Supplier<? extends Stream<? extends E>> streamSupplier) {
		return () -> {
			return (Stream<E>) streamSupplier.get();
		};
	}

	static <E> Seq<E> of(Collection<E> collection) {
		return requireNonNull(collection)::stream;
	}

	static <E> Seq<E> of(E entry) {
		return Collections.singleton(entry)::stream;
	}

	@SafeVarargs
	static <E> Seq<E> of(E entry, E... entries) {
		return of(entry).append().array(entries);
	}

	static <E> Seq<E> of(Iterable<E> iterable) {
		requireNonNull(iterable);
		return () -> StreamSupport.stream(iterable.spliterator(), false);
	}

	default Extender<L> append() {
		return o -> concat(this::stream, o);
	}

	default Extender<Object> appendAny() {
		return o -> concat(this::stream, o);
	}

	default String asString() {
		return toList().toString();
	}

	default <X> Seq<X> cast(Class<X> type) {
		return cast(type, true);
	}

	default <X> Seq<X> cast(Class<X> type, boolean failFast) {
		if (failFast && !stream().allMatch(type::isInstance)) {
			throw new ClassCastException("Could not cast " + this + " to " + type);
		} else {
			return map().to(type::cast);
		}
	}

	default Seq<L> compact() {
		return isFinite() ? toList()::stream : this;
	}

	default <X> boolean contains(Class<X> type) {
		return contains(type::isInstance);
	}

	default <X> boolean contains(Class<X> type, Predicate<? super X> predicate) {
		return stream().filter(type::isInstance).map(type::cast).anyMatch(predicate);
	}

	default <X> boolean contains(Object element) {
		return contains(element::equals);
	}

	default <X> boolean contains(Predicate<? super L> predicate) {
		return stream().anyMatch(predicate);
	}

	default boolean equal(Seq<L> other) {
		Iterator<L> iterator = other.iterator();
		return stream().allMatch(e -> Objects.equals(e, iterator.next()));
	}

	default Filter<L> filter() {
		return this::filter;
	}

	default <X> Seq<X> filter(Function<Seq<L>, Stream<X>> filter) {
		requireNonNull(filter);
		return () -> filter.apply(this);
	}

	default Optional<L> fold(BiFunction<? super L, ? super L, ? extends L> mapper) {
		return fold(Function.identity(), mapper);
	}

	default <T> Optional<T> fold(Function<? super L, ? extends T> creator,
			BiFunction<? super T, ? super L, ? extends T> mapper) {
		Optional<T> identity = head().map(creator);
		return tail().fold(identity, (o, e) -> o.map(t -> mapper.apply(t, e)));
	}

	default <T> T fold(T identity, BiFunction<? super T, ? super L, ? extends T> mapper) {
		T result = identity;
		for (L element : this) {
			result = mapper.apply(result, element);
		}
		return result;
	}

	default Optional<L> get(long index) {
		return stream().skip(index).findFirst();
	}

	default <K> Map<K, Seq<L>> groupBy(Function<? super L, K> mapper) {
		return map().grouped(mapper).toMap(tpl -> tpl.getLeft(), tpl -> tpl.getRight());
	}

	default Optional<L> head() {
		return stream().findFirst();
	}

	default boolean isEmpty() {
		return size() == 0L;
	}

	default boolean isFinite() {
		long size = size();
		return size >= 0L && size != Long.MAX_VALUE;
	}

	default boolean isNotEmpty() {
		return !isEmpty();
	}

	@Override
	default Iterator<L> iterator() {
		return stream().iterator();
	}

	default Joiner<L> join() {
		return this::join;
	}

	default <R> Seq<Tpl<L, R>> join(Seq<R> other, BiPredicate<L, R> predicate,
			Function<Extender<Tpl<L, R>>, Seq<Tpl<L, R>>> appender) {
		requireNonNull(other);
		requireNonNull(predicate);
		requireNonNull(appender);
		Seq<Tpl<L, R>> tuples = filter().nonNull().map()
				.flat(l -> other.filter().by(r -> r != null && predicate.test(l, r)).map().to(r -> Tpl.of(l, r)));
		return appender.apply(tuples.append());
	}

	default Optional<L> last() {
		return fold((t, e) -> e);
	}

	default Mapper<L> map() {
		return this::map;
	}

	default <X> Seq<X> map(Function<Seq<L>, Seq<X>> mapper) {
		requireNonNull(mapper);
		return mapper.apply(this);
	}

	default Joiner<L> pairwise() {
		return this::pairwise;
	}

	default <R> Seq<Tpl<L, R>> pairwise(Seq<R> other, BiPredicate<L, R> predicate,
			Function<Extender<Tpl<L, R>>, Seq<Tpl<L, R>>> appender) {
		requireNonNull(other);
		requireNonNull(predicate);
		requireNonNull(appender);
		Seq<Tpl<L, R>> tuples = map().to(() -> {
			Iterator<R> right = other.iterator();
			return left -> Tpl.of(left, right.next());
		}).filter().by(tpl -> tpl.test(predicate));
		return appender.apply(tuples.append());
	}

	default Extender<L> prepend() {
		return o -> concat(o, this::stream);
	}

	default Extender<Object> prependAny() {
		return o -> concat(o, this::stream);
	}

	default Seq<L> reverse() {
		List<L> result = toList();
		Collections.reverse(result);
		return result::stream;
	}

	default long size() {
		return stream().spliterator().getExactSizeIfKnown();
	}

	default Tpl<Seq<L>, Seq<L>> splitAt(long position) {
		return Tpl.of(filter().limit(position), filter().skip(position));
	}

	default Tpl<Optional<L>, Seq<L>> splitAtHead() {
		return Tpl.of(head(), tail());
	}

	Stream<L> stream();

	default Seq<L> tail() {
		return new Seq<L>() {

			@Override
			public long size() {
				// skipped stream have no size :(
				long size = Seq.this.size();
				if (size == 0) {
					return 0;
				} else if (size < 0) {
					return -1;
				} else {
					return size - 1;
				}
			}

			@Override
			public Stream<L> stream() {
				return Seq.this.stream().skip(1L);
			}
		};
	}

	default L[] toArray(IntFunction<L[]> generator) {
		return stream().toArray(generator);
	}

	default List<L> toList() {
		return stream().collect(Collectors.toList());
	}

	default <K> Map<K, L> toMap(Function<? super L, K> keyMapper) {
		return stream().collect(Collectors.toMap(keyMapper, Function.identity()));
	}

	default <K, V> Map<K, V> toMap(Function<? super L, K> keyMapper, Function<? super L, V> valueMapper) {
		return stream().collect(Collectors.toMap(keyMapper, valueMapper));
	}
}
