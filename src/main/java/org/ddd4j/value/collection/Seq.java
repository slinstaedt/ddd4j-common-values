package org.ddd4j.value.collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ddd4j.contract.Require;

@FunctionalInterface
public interface Seq<E> extends Iter.Able<E> {

	@FunctionalInterface
	interface Extender<E> {

		Seq<E> apply(Seq<? extends E> other);

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
			return apply(new ArrayList<E>(entries)::stream);
		}

		default Seq<E> entry(E entry) {
			return apply(Collections.singleton(entry)::stream);
		}

		default Seq<E> iterable(Iterable<? extends E> iterable) {
			List<E> list = new ArrayList<>();
			iterable.forEach(list::add);
			return apply(list::stream);
		}

		default Seq<E> nextIfAvailable(Iterator<? extends E> iterator) {
			return iterator.hasNext() ? entry(iterator.next()) : apply(Seq.empty());
		}

		default Seq<E> repeated(int repeat, E entry) {
			return apply(Collections.nCopies(repeat, entry)::stream);
		}

		default Seq<E> seq(Seq<? extends E> seq) {
			return apply(Require.nonNull(seq));
		}
	}

	@FunctionalInterface
	interface Filter<E> {

		<X> Seq<X> apply(Function<Seq<E>, Stream<X>> filter);

		default <X> Seq<X> applyStream(Function<Stream<E>, Stream<X>> filter) {
			Require.nonNull(filter);
			return apply(s -> filter.apply(s.stream()));
		}

		default Seq<E> by(Predicate<? super E> predicate) {
			Require.nonNull(predicate);
			return applyStream(s -> s.filter(predicate));
		}

		default Seq<E> by(Supplier<Predicate<? super E>> predicateSupplier) {
			Require.nonNull(predicateSupplier);
			return applyStream(s -> s.filter(predicateSupplier.get()));
		}

		default <X> Seq<X> byType(Class<? extends X> type) {
			Require.nonNull(type);
			return applyStream(s -> s.filter(type::isInstance).map(type::cast));
		}

		default Seq<E> distinct() {
			return distinct(Function.identity());
		}

		default Seq<E> distinct(Function<? super E, ?> keyMapper) {
			Require.nonNull(keyMapper);
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
			Require.nonNull(predicate);
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
			Require.nonNull(predicate);
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
			Require.nonNullElements(mapper, predicate);
			return apply(s -> s.stream().filter(e -> predicate.test(mapper.apply(s.map()))));
		}
	}

	@FunctionalInterface
	interface Joiner<L> {

		@FunctionalInterface
		interface InnerJoin<L, R, T> {

			Seq<T> apply(BiPredicate<? super L, ? super R> predicate, UnaryOperator<Opt<L>> leftFill, UnaryOperator<Opt<R>> rightFill);

			default InnerJoin<L, R, T> on(BiPredicate<? super L, ? super R> predicate) {
				return on(Function.identity(), Function.identity(), predicate);
			}

			default <X, Y> InnerJoin<L, R, T> on(Function<? super L, X> leftProperty, Function<? super R, Y> rightProperty,
					BiPredicate<? super X, ? super Y> predicate) {
				Require.nonNullElements(leftProperty, rightProperty, predicate);
				return (p, lf, rf) -> apply((l, r) -> p.test(l, r) && predicate.test(leftProperty.apply(l), rightProperty.apply(r)), lf, rf);
			}

			default InnerJoin<L, R, T> onEqual() {
				return on(Function.identity(), Function.identity(), Objects::equals);
			}

			default InnerJoin<L, R, T> onEqual(Function<? super L, ?> leftProperty, Function<? super R, ?> rightProperty) {
				return on(leftProperty, rightProperty, Objects::equals);
			}

			default Seq<T> result() {
				return apply((l, r) -> true, UnaryOperator.identity(), UnaryOperator.identity());
			}
		}

		@FunctionalInterface
		interface LeftJoin<L, R, T> extends InnerJoin<L, R, T> {

			default LeftJoin<L, R, T> withRightFilled(R right) {
				return (p, lf, rf) -> apply(p, lf, r -> Opt.of(right));
			}
		}

		@FunctionalInterface
		interface OuterJoin<L, R, T> extends LeftJoin<L, R, T>, RightJoin<L, R, T> {

			@Override
			default OuterJoin<L, R, T> withLeftFilled(L left) {
				return (p, lf, rf) -> apply(p, l -> Opt.of(left), rf);
			}

			@Override
			default OuterJoin<L, R, T> withRightFilled(R right) {
				return (p, lf, rf) -> apply(p, lf, r -> Opt.of(right));
			}
		}

		@FunctionalInterface
		interface RightJoin<L, R, T> extends InnerJoin<L, R, T> {

			default RightJoin<L, R, T> withLeftFilled(L left) {
				return (p, lf, rf) -> apply(p, l -> Opt.of(left), rf);
			}
		}

		<R, T> Seq<T> apply(Seq<R> other, BiFunction<? super L, ? super R, T> mapper, BiPredicate<? super L, ? super R> predicate, Opt<L> leftFill,
				Opt<R> rightFill);

		default <R> InnerJoin<L, R, Tpl<L, R>> inner(Seq<R> other) {
			return inner(other, Tpl::of);
		}

		default <R, T> InnerJoin<L, R, T> inner(Seq<R> other, BiFunction<? super L, ? super R, T> mapper) {
			Require.nonNullElements(other, mapper);
			return (p, lf, rf) -> apply(other, mapper, p, lf.apply(Opt.empty()), rf.apply(Opt.empty()));
		}

		default <R> LeftJoin<L, R, Tpl<L, R>> left(Seq<R> other) {
			return left(other, Tpl::of);
		}

		default <R, T> LeftJoin<L, R, T> left(Seq<R> other, BiFunction<? super L, ? super R, T> mapper) {
			Require.nonNullElements(other, mapper);
			return (p, lf, rf) -> apply(other, mapper, p, lf.apply(Opt.empty()), rf.apply(Opt.ofNull()));
		}

		default <R> OuterJoin<L, R, Tpl<L, R>> outer(Seq<R> other) {
			return outer(other, Tpl::of);
		}

		default <R, T> OuterJoin<L, R, T> outer(Seq<R> other, BiFunction<? super L, ? super R, T> mapper) {
			Require.nonNullElements(other, mapper);
			return (p, lf, rf) -> apply(other, mapper, p, lf.apply(Opt.ofNull()), rf.apply(Opt.ofNull()));
		}

		default <R> RightJoin<L, R, Tpl<L, R>> right(Seq<R> other) {
			return right(other, Tpl::of);
		}

		default <R, T> RightJoin<L, R, T> right(Seq<R> other, BiFunction<? super L, ? super R, T> mapper) {
			Require.nonNullElements(other, mapper);
			return (p, lf, rf) -> apply(other, mapper, p, lf.apply(Opt.ofNull()), rf.apply(Opt.empty()));
		}
	}

	@FunctionalInterface
	interface Mapper<E> {

		<X> Seq<X> apply(Function<Seq<E>, Seq<X>> mapper);

		default Seq<Tpl<E, E>> consecutivePairwise() {
			return toSupplied(() -> {
				Ref<E> last = Ref.create();
				return e -> last.update(t -> e);
			}).filter().skip(1);
		}

		default Seq<E> consecutiveScanned(BinaryOperator<E> operator) {
			Require.nonNull(operator);
			return consecutivePairwise().map().to(t -> t.fold(operator));
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
			Require.nonNull(mapper);
			return apply(s -> () -> s.stream().flatMap(mapper));
		}

		default <K> Seq<Tpl<K, Seq<E>>> grouped(Function<? super E, K> mapper) {
			Require.nonNull(mapper);
			return apply(s -> s.map().to(mapper).filter().distinct().map().to(k -> Tpl.of(k, s.filter().by(e -> Objects.equals(k, mapper.apply(e))))));
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
			Require.nonNull(mapper);
			return to(e -> Tpl.of(e, mapper.apply(e)));
		}

		default Seq<E> recursively(Function<? super E, Seq<E>> mapper) {
			Require.nonNull(mapper);
			return apply(s -> s.append().seq(flat(mapper).map().recursively(mapper)));
		}

		default Seq<E> recursivelyArray(Function<? super E, E[]> mapper) {
			Require.nonNull(mapper);
			return apply(s -> s.append().seq(flatArray(mapper).map().recursivelyArray(mapper)));
		}

		default Seq<E> recursivelyCollection(Function<? super E, Collection<? extends E>> mapper) {
			Require.nonNull(mapper);
			return apply(s -> s.append().seq(flatCollection(mapper).map().recursivelyCollection(mapper)));
		}

		default Seq<E> recursivelyStream(Function<? super E, Stream<? extends E>> mapper) {
			Require.nonNull(mapper);
			return apply(s -> s.append().seq(flatStream(mapper).map().recursivelyStream(mapper)));
		}

		default <X> Seq<X> to(Function<? super E, ? extends X> mapper) {
			Require.nonNull(mapper);
			return apply(s -> () -> s.stream().map(mapper));
		}

		default <X> Seq<X> toSupplied(Supplier<Function<? super E, ? extends X>> mapperSupplier) {
			Require.nonNull(mapperSupplier);
			return apply(s -> () -> s.stream().map(mapperSupplier.get()));
		}

		default Seq<Tpl<E, Long>> zipWithIndex() {
			return toSupplied(() -> {
				Ref<Long> index = Ref.of(0L);
				return e -> Tpl.of(e, index.getAndUpdate(t -> t++));
			});
		}
	}

	@FunctionalInterface
	interface ThrowingConsumer<E, T extends Throwable> {

		void accept(E entry) throws T;
	}

	@SuppressWarnings("unchecked")
	static <E> Seq<E> cast(Seq<? extends E> sequence) {
		Require.nonNull(sequence);
		return () -> {
			return (Stream<E>) sequence.stream();
		};
	}

	static <E> Seq<E> concat(Seq<? extends E> a, Seq<? extends E> b) {
		if (a.isEmpty()) {
			return Seq.cast(b);
		} else if (b.isEmpty()) {
			return Seq.cast(a);
		} else {
			return () -> Stream.concat(a.stream(), b.stream());
		}
	}

	static <E> Seq<E> empty() {
		return Stream::empty;
	}

	static <E> Seq<E> of(Collection<E> collection) {
		return new ArrayList<>(collection)::stream;
	}

	@SafeVarargs
	static <E> Seq<E> of(E... entries) {
		return Arrays.asList(entries)::stream;
	}

	static <E> Seq<E> of(Iterable<E> iterable) {
		return Iter.Able.wrap(iterable).asSequence();
	}

	static <E> Seq<E> ofRemaining(Iterator<E> iterator) {
		List<E> list = new ArrayList<>();
		iterator.forEachRemaining(list::add);
		return list::stream;
	}

	static <E> Seq<E> singleton(E entry) {
		return Collections.singleton(entry)::stream;
	}

	default Extender<E> append() {
		return o -> concat(this, o);
	}

	default Extender<Object> appendAny() {
		return o -> concat(this, o);
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

	default Seq<E> compact() {
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

	default <X> boolean contains(Predicate<? super E> predicate) {
		return stream().anyMatch(predicate);
	}

	default boolean equal(Seq<E> other) {
		Iterator<E> iterator = other.iterator();
		return stream().allMatch(e -> Objects.equals(e, iterator.next()));
	}

	default Filter<E> filter() {
		return this::filter;
	}

	default <X> Seq<X> filter(Function<Seq<E>, Stream<X>> filter) {
		Require.nonNull(filter);
		return () -> filter.apply(this);
	}

	default Optional<E> fold(BiFunction<? super E, ? super E, ? extends E> mapper) {
		return fold(Function.identity(), mapper);
	}

	default <T> Optional<T> fold(Function<? super E, ? extends T> creator, BiFunction<? super T, ? super E, ? extends T> mapper) {
		Optional<T> identity = head().map(creator);
		return tail().fold(identity, (o, e) -> o.map(t -> mapper.apply(t, e)));
	}

	default <T> T fold(T identity, BiFunction<? super T, ? super E, ? extends T> mapper) {
		T result = identity;
		for (E element : this) {
			result = mapper.apply(result, element);
		}
		return result;
	}

	default <T extends Throwable> void forEach(ThrowingConsumer<? super E, T> action) throws T {
		Iterator<E> iterator = iterator();
		while (iterator.hasNext()) {
			action.accept(iterator.next());
		}
	}

	default Optional<E> get(long index) {
		return filter().skip(index).head();
	}

	default <K> Map<K, Seq<E>> groupBy(Function<? super E, K> mapper) {
		return map().grouped(mapper).toMap(tpl -> tpl.getLeft(), tpl -> tpl.getRight());
	}

	default Optional<E> head() {
		return stream().findFirst();
	}

	default Seq<E> ifMatches(Predicate<? super Seq<E>> predicate, Function<? super Seq<E>, ? extends Seq<E>> function) {
		return predicate.test(this) ? function.apply(this) : this;
	}

	default Seq<E> intersect(Seq<? extends E> other, boolean anyInfinite) {
		Require.nonNull(other);
		return of(() -> {
			Iterator<? extends E> i1 = this.iterator();
			Iterator<? extends E> i2 = other.iterator();
			return new Iterator<E>() {

				private boolean useFirst = true;

				@Override
				public boolean hasNext() {
					return anyInfinite ? i1.hasNext() && i2.hasNext() : i1.hasNext() || i2.hasNext();
				}

				@Override
				public E next() {
					if (!hasNext()) {
						throw new NoSuchElementException();
					} else if (useFirst && i1.hasNext()) {
						useFirst = !i2.hasNext();
						return i1.next();
					} else if (i2.hasNext()) {
						useFirst = i1.hasNext();
						return i2.next();
					} else {
						throw new NoSuchElementException();
					}
				}
			};
		});

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
	default Iter<E> iter() {
		return Iter.wrap(stream().iterator());
	}

	@Override
	default Iterator<E> iterator() {
		return stream().iterator();
	}

	@Override
	default Spliterator<E> spliterator() {
		final long size = size();
		int characteristics = Iter.Able.super.spliterator().characteristics();
		if (size == 0L) {
			return Spliterators.emptySpliterator();
		} else if (size < 0L) {
			return Spliterators.spliteratorUnknownSize(iterator(), characteristics);
		} else {
			return Spliterators.spliterator(iterator(), size, characteristics);
		}
	}

	default Joiner<E> join() {
		return this::join;
	}

	default <R, T> Seq<T> join(Seq<R> other, BiFunction<? super E, ? super R, T> mapper, BiPredicate<? super E, ? super R> predicate, Opt<E> leftFill,
			Opt<R> rightFill) {
		Require.nonNullElements(other, mapper, predicate, leftFill, rightFill);
		return this.map()
				.flat(l -> other.filter()
						.by(r -> predicate.test(l, r))
						.ifMatches(Seq::isEmpty, s -> rightFill.applyNullable(e -> s.append().entry(e), () -> s))
						.map()
						.to(r -> mapper.apply(l, r)))
				.append()
				.seq(other.filter().by(r -> !leftFill.isEmpty() && this.stream().noneMatch(l -> predicate.test(l, r))).map().to(leftFill.asFunction(mapper)));
	}

	default Optional<E> last() {
		return fold((t, e) -> e);
	}

	default Mapper<E> map() {
		return this::map;
	}

	default <X> Seq<X> map(Function<Seq<E>, Seq<X>> mapper) {
		Require.nonNull(mapper);
		return mapper.apply(this);
	}

	default Extender<E> prepend() {
		return o -> concat(o, this);
	}

	default Extender<Object> prependAny() {
		return o -> concat(o, this);
	}

	default Seq<E> reverse() {
		List<E> result = toList();
		Collections.reverse(result);
		return result::stream;
	}

	default long size() {
		return stream().spliterator().getExactSizeIfKnown();
	}

	default Tpl<Seq<E>, Seq<E>> splitAt(long position) {
		return Tpl.of(filter().limit(position), filter().skip(position));
	}

	default Tpl<Optional<E>, Seq<E>> splitAtHead() {
		return Tpl.of(head(), tail());
	}

	Stream<E> stream();

	default Seq<E> tail() {
		return filter().skip(1);
	}

	default E[] toArray(IntFunction<E[]> generator) {
		return stream().toArray(generator);
	}

	default List<E> toList() {
		return stream().collect(Collectors.toList());
	}

	default <K> Map<K, E> toMap(Function<? super E, K> keyMapper) {
		return stream().collect(Collectors.toMap(keyMapper, Function.identity()));
	}

	default <K, V> Map<K, V> toMap(Function<? super E, K> keyMapper, Function<? super E, V> valueMapper) {
		return stream().collect(Collectors.toMap(keyMapper, valueMapper));
	}

	default Joiner<E> zip() {
		return this::zip;
	}

	default <R, T> Seq<T> zip(Seq<R> other, BiFunction<? super E, ? super R, T> mapper, BiPredicate<? super E, ? super R> predicate, Opt<E> leftFill,
			Opt<R> rightFill) {
		Require.nonNullElements(other, mapper, predicate, leftFill, rightFill);
		rightFill.fillNullable(other, Seq::isEmpty);
		Iter.Able<T> able = () -> {
			Ref<Tpl<E, R>> ref = Ref.create();
			Iter<E> i1 = this.iter();
			Iter<R> i2 = other.iter();
			return c -> i1.visitNext(e -> ref.update(tpl -> tpl.mapLeft(x -> e))) && i2.visitNext(r -> ref.update(tpl -> tpl.mapRight(x -> r)))
					&& ref.get().test(predicate) && c.acceptIf(() -> true, () -> ref.get().fold(mapper));
		};
		return able.asSequence();
	}
}
