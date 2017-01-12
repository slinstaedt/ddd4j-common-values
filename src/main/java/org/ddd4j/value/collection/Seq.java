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
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Opt;
import org.ddd4j.value.collection.Ref.RefTpl;
import org.ddd4j.value.collection.Seq.Mapper.Mapping;

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

		<X> X apply(Function<Seq<E>, X> filter);

		default <X> Seq<X> applyStream(Function<Stream<E>, Stream<X>> filter) {
			Require.nonNull(filter);
			return apply(s -> new Value<>(() -> filter.apply(s.stream())));
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

		default Tpl<Seq<E>, Seq<E>> splitAt(long position) {
			return Tpl.of(limit(position), skip(position));
		}

		default Tpl<Opt<E>, Seq<E>> splitAtHead() {
			return apply(s -> Tpl.of(s.head(), s.tail()));
		}

		default <X> Seq<E> where(Function<Mapper<E>, Mapping<E, X>> mapper, Predicate<? super X> predicate) {
			Require.nonNullElements(mapper, predicate);
			return apply(s -> mapper.apply(s.map()).filtered(predicate).source());
		}

		default <X> Seq<E> whereAll(Function<Mapper<E>, Mapping<E, Seq<X>>> mapper, Predicate<? super X> predicate) {
			Require.nonNullElements(mapper, predicate);
			return where(mapper, s -> s.stream().allMatch(predicate));
		}

		default <X> Seq<E> whereAny(Function<Mapper<E>, Mapping<E, Seq<X>>> mapper, Predicate<? super X> predicate) {
			Require.nonNullElements(mapper, predicate);
			return where(mapper, s -> s.stream().anyMatch(predicate));
		}

		default <X> Seq<E> whereNone(Function<Mapper<E>, Mapping<E, Seq<X>>> mapper, Predicate<? super X> predicate) {
			Require.nonNullElements(mapper, predicate);
			return where(mapper, s -> s.stream().noneMatch(predicate));
		}
	}

	@FunctionalInterface
	interface Folder<E> {

		default boolean allMatch(Predicate<? super E> predicate) {
			return apply(s -> s.allMatch(predicate));
		}

		default boolean anyMatch(Predicate<? super E> predicate) {
			return apply(s -> s.anyMatch(predicate));
		}

		<T, R> R apply(Function<? super E, ? extends T> mapper, Function<Stream<T>, R> downstream);

		default <R> R apply(Function<Stream<E>, R> downstream) {
			return apply(Function.identity(), downstream);
		}

		default <A, R> R collect(Collector<? super E, A, R> collector) {
			return collect(Function.identity(), collector);
		}

		default <T, A, R> R collect(Function<? super E, ? extends T> mapper, Collector<? super T, A, R> collector) {
			return apply(mapper, s -> s.collect(collector));
		}

		default long counting() {
			return collect(Collectors.counting());
		}

		default <T> T eachWithIdentity(T identity, BiFunction<? super T, ? super E, ? extends T> fold) {
			Ref<T> ref = Ref.create(identity);
			apply(Function.identity()).forEach(e -> ref.update(t -> fold.apply(t, e)));
			return ref.get();
		}

		default <T> Optional<T> eachWithIntitial(Function<? super E, ? extends T> initial, BiFunction<? super T, ? super E, ? extends T> fold) {
			Ref<T> ref = Ref.create();
			apply(Function.identity()).forEach(e -> ref.update(t -> t == null ? initial.apply(e) : fold.apply(t, e)));
			return Optional.ofNullable(ref.get());
		}

		default <K> Map<K, List<E>> groupingBy(Function<? super E, ? extends K> classifier) {
			return collect(Collectors.groupingBy(classifier));
		}

		default <K, A, D> Map<K, D> groupingBy(Function<? super E, ? extends K> classifier, Collector<? super E, A, D> downstream) {
			return collect(Collectors.groupingBy(classifier, downstream));
		}

		default <K, D, A, M extends Map<K, D>> M groupingBy(Function<? super E, ? extends K> classifier, Supplier<M> mapFactory,
				Collector<? super E, A, D> downstream) {
			return collect(Collectors.groupingBy(classifier, mapFactory, downstream));
		}

		default boolean isEmpty() {
			return counting() == 0L;
		}

		default String joined() {
			return apply(Objects::toString, s -> s.collect(Collectors.joining()));
		}

		default String joined(CharSequence delimiter) {
			return collect(Objects::toString, Collectors.joining(delimiter));
		}

		default String joined(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
			return collect(Objects::toString, Collectors.joining(delimiter, prefix, suffix));
		}

		default Optional<E> last() {
			return reduce((t, e) -> e);
		}

		default boolean noneMatch(Predicate<? super E> predicate) {
			return apply(s -> s.noneMatch(predicate));
		}

		default Optional<E> reduce(BinaryOperator<E> accumulator) {
			return reduce(Function.identity(), accumulator);
		}

		default E reduce(E identity, BinaryOperator<E> accumulator) {
			return reduce(Function.identity(), identity, accumulator);
		}

		default <T> Optional<T> reduce(Function<? super E, T> mapper, BinaryOperator<T> accumulator) {
			return apply(mapper, s -> s.reduce(accumulator));
		}

		default <T> T reduce(Function<? super E, T> mapper, T identity, BinaryOperator<T> accumulator) {
			return apply(mapper, s -> s.reduce(identity, accumulator));
		}

		default <T, U> U reduce(Function<? super E, T> mapper, U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
			return apply(mapper, s -> s.reduce(identity, accumulator, combiner));
		}

		default <U> U reduce(U identity, BiFunction<U, ? super E, U> accumulator, BinaryOperator<U> combiner) {
			return reduce(Function.identity(), identity, accumulator, combiner);
		}

		default Seq<E> reverse() {
			return apply(s -> {
				List<E> result = s.collect(Collectors.toList());
				Collections.reverse(result);
				return result::stream;
			});
		}

		default List<E> toList() {
			return collect(Collectors.toList());
		}

		default <K> Map<K, E> toMap(Function<? super E, K> keyMapper) {
			return collect(Collectors.toMap(keyMapper, Function.identity()));
		}

		default <K, V> Map<K, V> toMap(Function<? super E, K> keyMapper, Function<? super E, V> valueMapper) {
			return collect(Collectors.toMap(keyMapper, valueMapper));
		}

		default Set<E> toSet() {
			return collect(Collectors.toSet());
		}

		default <T extends Exception> void toString(ThrowingConsumer<String, T> consumer) throws T {
			consumer.accept(joined(", ", "[", "]"));
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
			return (p, lf, rf) -> apply(other, mapper, p, lf.apply(Opt.none()), rf.apply(Opt.none()));
		}

		default <R> LeftJoin<L, R, Tpl<L, R>> left(Seq<R> other) {
			return left(other, Tpl::of);
		}

		default <R, T> LeftJoin<L, R, T> left(Seq<R> other, BiFunction<? super L, ? super R, T> mapper) {
			Require.nonNullElements(other, mapper);
			return (p, lf, rf) -> apply(other, mapper, p, lf.apply(Opt.none()), rf.apply(Opt.ofNull()));
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
			return (p, lf, rf) -> apply(other, mapper, p, lf.apply(Opt.ofNull()), rf.apply(Opt.none()));
		}
	}

	@FunctionalInterface
	interface Mapper<E> {

		@FunctionalInterface
		interface Mapping<S, T> extends Seq<Tpl<S, T>> {

			static <S, T> Mapping<S, T> wrap(Seq<Tpl<S, T>> sequence) {
				return Require.nonNull(sequence)::stream;
			}

			default <A, R> Mapping<S, R> collect(Collector<? super T, A, R> downstream) {
				Require.nonNull(downstream);
				// TODO use collector?
				return grouped().mapped(s -> s.fold().collect(downstream));
			}

			default Mapping<S, T> filtered(Predicate<? super T> predicate) {
				Require.nonNull(predicate);
				return () -> stream().filter(tpl -> tpl.testRight(predicate));
			}

			default Seq<T> get(S key) {
				return wrap(filter().by(tpl -> tpl.equalsLeft(key))).target();
			}

			default Mapping<S, Seq<T>> grouped() {
				// TODO test
				return source().filter().distinct().map().mapped(s -> wrap(filter().by(tpl -> tpl.equalsLeft(s))).target());
			}

			default <X> Mapping<S, X> mapped(Function<? super T, ? extends X> mapper) {
				Require.nonNull(mapper);
				return () -> stream().map(tpl -> tpl.mapRight(mapper));
			}

			default Mapping<S, T> put(S key, T value) {
				return append().entry(Tpl.of(key, value))::stream;
			}

			default Mapping<T, S> reversed() {
				return () -> stream().map(Tpl::reverse);
			}

			default Seq<S> source() {
				return new Value<>(() -> stream().map(Tpl::getLeft));
			}

			default Seq<T> target() {
				return new Value<>(() -> stream().map(Tpl::getRight));
			}

			default <X> Seq<X> to(BiFunction<? super S, ? super T, ? extends X> mapper) {
				return map().to(tpl -> tpl.fold(mapper));
			}

			default Index<S, T> toIndex() {
				// TODO
				return null;
			}

			default Map<S, T> toMap() {
				return fold().toMap(Tpl::getLeft, Tpl::getRight);
			}
		}

		<X, Y> Mapping<X, Y> apply(Function<Mapping<E, E>, Seq<Tpl<X, Y>>> function);

		default <X, Y> Mapping<X, Y> applyStream(Function<Mapping<E, E>, Stream<Tpl<X, Y>>> mapper) {
			return apply(m -> new Value<>(() -> mapper.apply(m)));
		}

		default <X> Mapping<E, X> cast(Class<X> type) {
			return cast(type, true);
		}

		default <X> Mapping<E, X> cast(Class<X> type, boolean failFast) {
			if (failFast && !to(type::isInstance).fold().reduce(Boolean.TRUE, Boolean::logicalAnd)) {
				throw new ClassCastException("Could not cast " + this + " to " + type);
			} else {
				return mapped(type::cast);
			}
		}

		default Mapping<E, E> consecutivePairwise() {
			return toSupplied(() -> {
				Ref<E> last = Ref.create();
				return e -> last.getAndUpdate(x -> e);
			}).filter().skip(1)::stream;
		}

		default Seq<E> consecutiveScanned(BinaryOperator<E> operator) {
			Require.nonNull(operator);
			return consecutivePairwise().to(operator);
		}

		default <X> Seq<X> flat(Function<? super E, ? extends Seq<X>> mapper) {
			return flatStream(mapper.andThen(Seq::stream)).target();
		}

		default <X> Mapping<E, X> flatArray(Function<? super E, X[]> mapper) {
			return flatStream(mapper.andThen(Stream::of));
		}

		default <X> Mapping<E, X> flatCollection(Function<? super E, ? extends Collection<? extends X>> mapper) {
			return flatStream(mapper.andThen(Collection::stream));
		}

		default <X> Mapping<E, X> flatSeq(Function<? super E, ? extends Seq<? extends X>> mapper) {
			return flatStream(mapper.andThen(Seq::stream));
		}

		default <X> Mapping<E, X> flatStream(Function<? super E, ? extends Stream<? extends X>> mapper) {
			Require.nonNull(mapper);
			return applyStream(m -> m.stream().flatMap(tpl -> tpl.flatMapRight(mapper)));
		}

		default <K> Mapping<K, Seq<E>> grouped(Function<? super E, K> classifier) {
			return mapped(classifier).reversed().grouped();
		}

		default Mapping<E, Long> indexed() {
			return toSupplied(() -> {
				Ref<Long> index = Ref.of(0L);
				return e -> index.getAndUpdate(t -> t++);
			});
		}

		default <X> Mapping<E, X> mapped(Function<? super E, ? extends X> mapper) {
			Require.nonNull(mapper);
			return applyStream(m -> m.stream().map(tpl -> tpl.mapRight(mapper)));
		}

		default <X> Mapping<E, Seq<X>> mappedArray(Function<? super E, X[]> mapper) {
			return mapped(mapper.andThen(Seq::of));
		}

		default <X> Mapping<E, Seq<X>> mappedCollection(Function<? super E, ? extends Collection<X>> mapper) {
			return mapped(mapper.andThen(Seq::of));
		}

		default <X> Mapping<E, Seq<X>> mappedStream(Function<? super E, ? extends Stream<X>> mapper) {
			return mapped(e -> new Value<>(() -> mapper.apply(e)));
		}

		default Mapping<Long, Seq<E>> partition(long partitionSize) {
			return indexed().mapped(i -> i % partitionSize).reversed().grouped();
		}

		default Tpl<Seq<E>, Seq<E>> partition(Predicate<? super E> predicate) {
			Map<Boolean, Seq<E>> map = grouped(Require.nonNull(predicate)::test).toMap();
			return Tpl.of(map.getOrDefault(Boolean.TRUE, Seq.empty()), map.getOrDefault(Boolean.FALSE, Seq.empty()));
		}

		default Mapping<E, E> recursively(Function<? super E, ? extends Seq<? extends E>> mapper) {
			Require.nonNull(mapper);
			return apply(m -> m.append().seq(this.<E>flatSeq(mapper).target().map().recursively(mapper)));
		}

		default Mapping<E, E> recursivelyArray(Function<? super E, E[]> mapper) {
			Require.nonNull(mapper);
			return apply(m -> m.append().seq(flatArray(mapper).target().map().recursivelyArray(mapper)));
		}

		default Mapping<E, E> recursivelyCollection(Function<? super E, ? extends Collection<? extends E>> mapper) {
			Require.nonNull(mapper);
			return apply(m -> m.append().seq(this.<E>flatCollection(mapper).target().map().recursivelyCollection(mapper)));
		}

		default Mapping<E, E> recursivelyStream(Function<? super E, ? extends Stream<? extends E>> mapper) {
			Require.nonNull(mapper);
			return apply(m -> m.append().seq(this.<E>flatStream(mapper).target().map().recursivelyStream(mapper)));
		}

		default <X> Seq<X> to(Function<? super E, X> mapper) {
			return mapped(mapper).target();
		}

		default <X> Mapping<E, X> toSupplied(Supplier<Function<? super E, ? extends X>> mapperSupplier) {
			Require.nonNull(mapperSupplier);
			return applyStream(m -> m.stream().map(tpl -> tpl.mapRight(mapperSupplier.get())));
		}

		default Mapping<E, String> toText() {
			return mapped(Objects::toString);
		}
	}

	@FunctionalInterface
	interface ThrowingConsumer<E, T extends Exception> {

		void accept(E entry) throws T;
	}

	class Value<E> extends org.ddd4j.value.Value.Simple<Value<E>, List<E>> implements Seq<E> {

		private final Supplier<Stream<E>> value;

		public Value(Supplier<Stream<E>> value) {
			this.value = Require.nonNull(value);
		}

		@Override
		public Stream<E> stream() {
			return value.get();
		}

		@Override
		protected List<E> value() {
			return fold().toList();
		}
	}

	@SuppressWarnings("unchecked")
	static <E> Seq<E> cast(Seq<? extends E> sequence) {
		Require.nonNull(sequence);
		return new Value<>(() -> {
			return (Stream<E>) sequence.stream();
		});
	}

	static <E> Seq<E> concat(Seq<? extends E> a, Seq<? extends E> b) {
		if (a.isEmpty()) {
			return Seq.cast(b);
		} else if (b.isEmpty()) {
			return Seq.cast(a);
		} else {
			return new Value<>(() -> Stream.concat(a.stream(), b.stream()));
		}
	}

	static <E> Seq<E> empty() {
		return Stream::empty;
	}

	// XXX remove
	public static void main(String[] args) {
		System.out.println(Seq.of(null, "xxx").head().getNullable());
		Seq.of("1", "1 22", "22 333", "1 22 333")
				.filter()
				.whereAny(m -> m.mappedArray(s -> s.split("\\s")), s -> s.length() >= 2)
				.fold()
				.toString(System.out::println);
		Seq.of("1", "22", "333").filter().where(m -> m.mapped(String::length), l -> l >= 2).fold().toString(System.out::println);
		Seq.of("a", "b", "c").join().inner(Seq.of(1, 2), (x, y) -> x + y).result().fold().toString(System.out::println);
		Seq.of("a", "b", "c").zip().inner(Seq.of(1, 2), (x, y) -> x + y).result().fold().toString(System.out::println);
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
		return fold().toList().toString();
	}

	default Seq<E> checkFinite() {
		if (!isFinite()) {
			throw new IllegalStateException("illegal operation on infinite stream");
		}
		return this;
	}

	default Seq<E> compact() {
		return isFinite() ? fold().toList()::stream : this;
	}

	// TODO move to fold?
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

	default <X> X filter(Function<Seq<E>, X> filter) {
		Require.nonNull(filter);
		return filter.apply(this);
	}

	default Folder<E> fold() {
		return this::fold;
	}

	default <T, R> R fold(Function<? super E, ? extends T> mapper, Function<Stream<T>, R> downstream) {
		return downstream.apply(checkFinite().stream().map(mapper));
	}

	default <T extends Exception> void forEachThrowing(ThrowingConsumer<? super E, T> action) throws T {
		Iterator<E> iterator = iterator();
		while (iterator.hasNext()) {
			action.accept(iterator.next());
		}
	}

	default Opt<E> get(long index) {
		return filter().skip(index).head();
	}

	default Opt<E> head() {
		return stream().map(Opt::of).findFirst().orElse(Opt.none());
	}

	default Seq<E> ifMatches(Predicate<? super Seq<E>> predicate, Function<? super Seq<E>, ? extends Seq<E>> function) {
		Require.nonNullElements(predicate, function);
		return new Value<>(() -> predicate.test(this) ? function.apply(this).stream() : this.stream());
	}

	/**
	 * Seq.of(1, 2, 3).intersect().inner(Seq.of("a", "b", "c"), String::concat) -> Seq.of("1null", "1a", "2a", "2b", "3b", "3c")
	 */
	default Joiner<E> intersect() {
		return this::intersect;
	}

	// TODO use Joiner?
	default Seq<E> intersect(Seq<E> other, boolean anyInfinite) {
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

	default <R, T> Seq<T> intersect(Seq<R> other, BiFunction<? super E, ? super R, T> mapper, BiPredicate<? super E, ? super R> predicate, Opt<E> leftFill,
			Opt<R> rightFill) {
		Require.nonNullElements(other, mapper, predicate, leftFill, rightFill);
		Iter.Able<E> able = () -> {
			RefTpl<E, R> next = RefTpl.create(null, null);
			Function<Iter<E>, T> visitNext1 = i -> i.visitNextOrOptional(next::setLeft, leftFill) ? next.fold(mapper) : next.fold(mapper);
			RefTpl<Iter<E>, Iter<R>> iterators = RefTpl.create(this.iter(), other.iter());
			// iterators.getAndUpdate(Tpl::reverse).fold((i1, i2) -> visitNext.apply(i1).orElseMapGet(() ->
			// visitNext.apply(i2)));
			// TODO
			return () -> null;
		};
		return null;
	}

	default boolean isEmpty() {
		return sizeIfKnown() == 0L || head().isNotEmpty();
	}

	default boolean isFinite() {
		long size = sizeIfKnown();
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
				.seq(other.filter().by(r -> !leftFill.isEmpty() && this.stream().noneMatch(l -> predicate.test(l, r))).map().to(
						r -> leftFill.applyNullable(e -> mapper.apply(e, r))));
	}

	default int length() {
		long size = checkFinite().sizeIfKnown();
		return size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
	}

	default Mapper<E> map() {
		return this::map;
	}

	default <X, Y> Mapping<X, Y> map(Function<Mapping<E, E>, Seq<Tpl<X, Y>>> function) {
		return Require.nonNull(function).apply(() -> stream().map(Tpl::both))::stream;
	}

	default Extender<E> prepend() {
		return o -> concat(o, this);
	}

	default Extender<Object> prependAny() {
		return o -> concat(o, this);
	}

	default long sizeIfKnown() {
		return stream().spliterator().getExactSizeIfKnown();
	}

	@Override
	default Spliterator<E> spliterator() {
		final long size = sizeIfKnown();
		int characteristics = Iter.Able.super.spliterator().characteristics();
		if (size == 0L) {
			return Spliterators.emptySpliterator();
		} else if (size < 0L) {
			return Spliterators.spliteratorUnknownSize(iterator(), characteristics);
		} else {
			return Spliterators.spliterator(iterator(), size, characteristics);
		}
	}

	Stream<E> stream();

	default Seq<E> tail() {
		return filter().skip(1);
	}

	default Object[] toArray() {
		return stream().toArray();
	}

	default E[] toArray(IntFunction<E[]> generator) {
		return stream().toArray(generator);
	}

	/**
	 * Seq.of(1, 2, 3).zip().inner(Seq.of("a", "b", "c"), String::concat) -> Seq.of("1a", "2b", "3c")
	 */
	default Joiner<E> zip() {
		return this::zip;
	}

	default <R, T> Seq<T> zip(Seq<R> other, BiFunction<? super E, ? super R, T> mapper, BiPredicate<? super E, ? super R> predicate, Opt<E> leftFill,
			Opt<R> rightFill) {
		Require.nonNullElements(other, mapper, predicate, leftFill, rightFill);
		Iter.Able<T> able = () -> {
			RefTpl<E, R> ref = RefTpl.create(null, null);
			Iter<E> i1 = this.iter();
			Iter<R> i2 = other.iter();
			// TODO
			// return c -> i1.visitNextOrOptional(ref::setLeft, leftFill) | i2.visitNextOrOptional(ref::setRight,
			// rightFill) && ref.get().test(predicate) &&
			// c.acceptIf(() -> true, () -> ref.get().fold(mapper));
			return null;
		};
		return able.asSequence();
	}
}
