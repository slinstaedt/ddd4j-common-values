package org.ddd4j.value.collection;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@FunctionalInterface
public interface Seq<E> extends Iterable<E> {

	@FunctionalInterface
	interface Extender<E> {

		Seq<E> apply(Supplier<? extends Stream<? extends E>> other);

		default Seq<E> array(E[] entries) {
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

		default Seq<E> seq(Seq<? extends E> seq) {
			return apply(requireNonNull(seq)::stream);
		}
	}

	@FunctionalInterface
	interface Filterer<E, T> {

		class Contains<T> implements Supplier<Predicate<T>> {

			private final Function<? super T, ?> keyMapper;

			public Contains(Function<? super T, ?> keyMapper) {
				this.keyMapper = requireNonNull(keyMapper);
			}

			@Override
			public Predicate<T> get() {
				return new Predicate<T>() {

					private final Set<Object> visited = new HashSet<>();

					@Override
					public boolean test(T t) {
						return visited.add(keyMapper.apply(t));
					}
				};
			}
		}

		class Match<T> implements Supplier<Predicate<T>> {

			private final Predicate<? super T> predicate;
			private final boolean predicateOutcome;

			public Match(Predicate<? super T> predicate, boolean predicateOutcome) {
				this.predicate = requireNonNull(predicate);
				this.predicateOutcome = predicateOutcome;
			}

			@Override
			public Predicate<T> get() {
				return new Predicate<T>() {

					private boolean switched = false;

					@Override
					public boolean test(T t) {
						if (!switched) {
							switched = predicateOutcome ^ predicate.test(t);
						}
						return switched ? !predicateOutcome : predicateOutcome;
					}
				};
			}
		}

		static <E> Filterer<E, E> of(Seq<E> seq) {
			requireNonNull(seq);
			return () -> Tpl.of(seq, seq);
		}

		default Seq<E> by(Predicate<? super E> predicate) {
			return sequences().foldLeft(s -> s.filter(predicate));
		}

		default Seq<E> by(Supplier<Predicate<? super T>> predicate) {
			return sequences(Function.identity(), predicate);
		}

		default <X> Filterer<E, X> byType(Class<X> type) {
			return matches(type::isInstance).where(type::cast);
		}

		default Seq<E> distinct() {
			return distinct(Function.identity());
		}

		default Seq<E> distinct(Function<? super E, ?> keyMapper) {
			return matches(new Contains<>(keyMapper));
		}

		default Seq<E> limitUntil(Predicate<? super E> predicate) {
			return limitWhile(predicate.negate());
		}

		default Seq<E> limitWhile(Predicate<? super E> predicate) {
			return matches(new Match<>(predicate, true));
		}

		default Filterer<E, T> matches(Predicate<? super T> predicate) {
			requireNonNull(predicate);
			return matches(() -> predicate);
		}

		default Filterer<E, T> matches(Supplier<Predicate<? super T>> predicateSupplier) {
			requireNonNull(predicateSupplier);
			return () -> sequences().mapRight(s -> {
				Predicate<? super T> predicate = predicateSupplier.get();
				return s.filter(predicate);
			});
		}

		Tpl<Seq<E>, Seq<T>> sequences();

		default Seq<E> skipUntil(Predicate<? super E> predicate) {
			return matches(new Match<>(predicate, false));
		}

		default Seq<E> skipWhile(Predicate<? super E> predicate) {
			return skipUntil(predicate.negate());
		}

		default <X> Filterer<E, X> where(Function<? super T, X> mapper) {
			requireNonNull(mapper);
			return () -> sequences().mapRight(s -> s.map(mapper));
		}

		default <X> Filterer<E, Seq<X>> whereArray(Function<? super T, X[]> mapper) {
			return whereStream(mapper.andThen(Stream::of));
		}

		default <X> Filterer<E, Seq<X>> whereCollection(Function<? super T, Collection<X>> mapper) {
			return whereStream(mapper.andThen(Collection::stream));
		}

		default Filterer<E, T> whereRecursive(Function<? super T, Stream<? extends T>> mapper) {
			// TODO
			return null;
		}

		default <X> Filterer<E, Seq<X>> whereStream(Function<? super T, Stream<X>> mapper) {
			requireNonNull(mapper);
			return where(t -> () -> mapper.apply(t));
		}
	}

	@FunctionalInterface
	interface Mapper<E> {

		default <X> Seq<X> flat(Function<? super E, ? extends Seq<? extends X>> mapper) {
			return flatStream(mapper.andThen(Seq::stream));
		}

		default <X> Seq<X> flatArray(Function<? super E, X[]> mapper) {
			return flatStream(mapper.andThen(Stream::of));
		}

		default <X> Seq<X> flatCollection(Function<? super E, ? extends Collection<? extends X>> mapper) {
			return flatStream(mapper.andThen(Collection::stream));
		}

		default <X> Seq<X> flatStream(Function<? super E, ? extends Stream<? extends X>> mapper) {
			requireNonNull(mapper);
			return () -> sequence().stream().flatMap(mapper);
		}

		default Seq<E> recursively(Function<? super E, ? extends Seq<E>> mapper) {
			return sequence().append().seq(flat(mapper).map().recursively(mapper));
		}

		default Seq<E> recursivelyArray(Function<? super E, E[]> mapper) {
			return sequence().append().seq(flatArray(mapper).map().recursivelyArray(mapper));
		}

		default Seq<E> recursivelyCollection(Function<? super E, ? extends Collection<E>> mapper) {
			return sequence().append().seq(flatCollection(mapper).map().recursivelyCollection(mapper));
		}

		default Seq<E> recursivelyStream(Function<? super E, ? extends Stream<E>> mapper) {
			return sequence().append().seq(flatStream(mapper).map().recursivelyStream(mapper));
		}

		Seq<E> sequence();

		default <X> Seq<X> to(Function<? super E, ? extends X> mapper) {
			requireNonNull(mapper);
			return () -> sequence().stream().map(mapper);
		}

		default E[] toArray(IntFunction<E[]> generator) {
			return sequence().stream().toArray(generator);
		}

		default List<E> toList() {
			return sequence().stream().collect(Collectors.toList());
		}
	}

	static <E> Seq<E> concat(Supplier<? extends Stream<? extends E>> a, Supplier<? extends Stream<? extends E>> b) {
		if (Seq.of(a).isEmpty()) {
			return Seq.ofAny(b);
		} else if (Seq.of(b).isEmpty()) {
			return Seq.ofAny(a);
		} else {
			return () -> Stream.concat(a.get(), b.get());
		}
	}

	static <E> Seq<E> empty() {
		return Stream::empty;
	}

	static <E> Seq<E> of(Supplier<? extends Stream<E>> streamSupplier) {
		return requireNonNull(streamSupplier)::get;
	}

	@SuppressWarnings("unchecked")
	static <E> Seq<E> ofAny(Supplier<? extends Stream<? extends E>> streamSupplier) {
		return (Seq<E>) of(streamSupplier);
	}

	default Extender<E> append() {
		return o -> concat(this::stream, o);
	}

	default Extender<Object> appendAny() {
		return o -> concat(this::stream, o);
	}

	default String asString() {
		return map().toList().toString();
	}

	default <X> Seq<X> cast(Class<X> type) {
		return cast(type, true);
	}

	default <X> Seq<X> cast(Class<X> type, boolean failFast) {
		if (failFast && !stream().allMatch(type::isInstance)) {
			throw new ClassCastException("Could not cast " + this + " to " + type);
		} else {
			return map(type::cast);
		}
	}

	default Seq<E> compact() {
		return map().toList()::stream;
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

	default Filterer<E, E> filter() {
		return Filterer.of(this);
	}

	default Seq<E> filter(Predicate<? super E> predicate) {
		requireNonNull(predicate);
		return () -> stream().filter(predicate);
	}

	default <X> Seq<E> filterWhere(Function<? super E, ? extends X> mapper, Predicate<? super X> filter) {
		requireNonNull(mapper);
		requireNonNull(filter);
		return () -> stream().filter(e -> filter.test(mapper.apply(e)));
	}

	default <T> Optional<T> fold(Function<? super E, ? extends T> creator,
			BiFunction<? super T, ? super E, ? extends T> mapper) {
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

	default Optional<E> get(long index) {
		return stream().skip(index).findFirst();
	}

	default Optional<E> head() {
		return stream().findFirst();
	}

	default boolean isEmpty() {
		return size() == 0L;
	}

	default boolean isNotEmpty() {
		return !isEmpty();
	}

	@Override
	default Iterator<E> iterator() {
		return stream().iterator();
	}

	default Optional<E> last() {
		return this.<E> fold(Function.identity(), (t, e) -> e);
	}

	default Mapper<E> map() {
		return () -> this;
	}

	default <X> Seq<X> map(Function<? super E, ? extends X> mapper) {
		requireNonNull(mapper);
		return () -> stream().map(mapper);
	}

	default Extender<E> prepend() {
		return o -> concat(o, this::stream);
	}

	default Extender<Object> prependAny() {
		return o -> concat(o, this::stream);
	}

	default Seq<E> reverse() {
		List<E> result = map().toList();
		Collections.reverse(result);
		return result::stream;
	}

	default long size() {
		return stream().spliterator().getExactSizeIfKnown();
	}

	Stream<E> stream();

	default Seq<E> tail() {
		return new Seq<E>() {

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
			public Stream<E> stream() {
				return Seq.this.stream().skip(1L);
			}
		};
	}
}
