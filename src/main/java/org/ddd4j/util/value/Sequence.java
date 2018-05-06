package org.ddd4j.util.value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ddd4j.util.Require;
import org.ddd4j.util.collection.Array;

public interface Sequence<E> extends Iterable<E> {

	static <E> Sequence<E> empty() {
		return Stream::empty;
	}

	@SafeVarargs
	static <E> Sequence<E> of(E... values) {
		Require.nonNull(values);
		return () -> Stream.of(values);
	}

	static <E> Sequence<E> of(Supplier<Stream<E>> source) {
		return Require.nonNull(source)::get;
	}

	static <E> Sequence<E> ofCopied(Collection<E> collection) {
		return new ArrayList<>(collection)::stream;
	}

	static <E> Sequence<E> ofCopied(Stream<E> stream) {
		return stream.collect(Collectors.toList())::stream;
	}

	default boolean contains(Object candidate) {
		return stream().anyMatch(candidate::equals);
	}

	default Sequence<E> copy() {
		return new Array<E>(size()).addAll(iterator());
	}

	default Sequence<E> filter(Predicate<? super E> predicate) {
		return () -> stream().filter(predicate);
	}

	default <X> Sequence<X> flatMap(Function<? super E, Stream<? extends X>> mapper) {
		return () -> stream().flatMap(mapper);
	}

	default E get(int index) {
		return getOptional(index).orElseThrow(() -> new IndexOutOfBoundsException(index));
	}

	default Optional<E> getOptional(int index) {
		return stream().skip(index).findFirst();
	}

	default <K, V> Map<K, Sequence<V>> groupBy(Function<? super E, ? extends K> key, Function<? super E, ? extends V> value) {
		Collector<? super E, ?, List<V>> c1 = Collectors.mapping(value, Collectors.<V>toList());
		Collector<? super E, ?, Sequence<V>> c2 = Collectors.collectingAndThen(c1, l -> of(l::stream));
		return stream().collect(Collectors.groupingBy(key, c2));
	}

	default <K> Map<K, Sequence<E>> groupBy(Function<? super E, K> key) {
		return groupBy(key, Function.identity());
	}

	default E head() {
		return headOptional().orElseThrow(NoSuchElementException::new);
	}

	default Optional<E> headOptional() {
		return stream().findFirst();
	}

	default Sequence<E> ifEmpty(Runnable whenEmpty) {
		if (isEmpty()) {
			whenEmpty.run();
		}
		return this;
	}

	default Sequence<E> ifNotEmpty(Runnable whenNotEmpty) {
		if (isNotEmpty()) {
			whenNotEmpty.run();
		}
		return this;
	}

	default boolean isEmpty() {
		return size() == 0;
	}

	default boolean isNotEmpty() {
		return !isEmpty();
	}

	@Override
	default Iterator<E> iterator() {
		return stream().iterator();
	}

	default E last() {
		return lastOptional().orElseThrow(NoSuchElementException::new);
	}

	default Optional<E> lastOptional() {
		return stream().reduce((e1, e2) -> e2);
	}

	default <X> Sequence<X> map(Function<? super E, ? extends X> mapper) {
		return () -> stream().map(mapper);
	}

	default int size() {
		long size = sizeIfKnown();
		return size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
	}

	default long sizeIfKnown() {
		return stream().spliterator().getExactSizeIfKnown();
	}

	Stream<E> stream();

	default E[] toArray(IntFunction<E[]> generator) {
		return stream().toArray(generator);
	}

	default List<E> toList() {
		return stream().collect(Collectors.toList());
	}

	default Sequence<E> visit(Consumer<? super E> consumer) {
		forEach(consumer);
		return this;
	}
}
