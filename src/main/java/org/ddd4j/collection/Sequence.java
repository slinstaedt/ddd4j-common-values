package org.ddd4j.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ddd4j.Require;

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

	static <E> Sequence<E> ofCopied(Supplier<Stream<E>> source) {
		return source.get().collect(Collectors.toList())::stream;
	}

	static <E> Sequence<E> ofCopied(Collection<E> collection) {
		return new ArrayList<>(collection)::stream;
	}

	default Sequence<E> copy() {
		return toList()::stream;
	}

	default Sequence<E> filter(Predicate<? super E> predicate) {
		return () -> stream().filter(predicate);
	}

	default <X> Sequence<X> flatMap(Function<? super E, Stream<? extends X>> mapper) {
		return () -> stream().flatMap(mapper);
	}

	default <K> Map<K, Sequence<E>> groupBy(Function<? super E, K> key) {
		return groupBy(key, Function.identity());
	}

	default <K, V> Map<K, Sequence<V>> groupBy(Function<? super E, ? extends K> key, Function<? super E, ? extends V> value) {
		Collector<? super E, ?, List<V>> c1 = Collectors.mapping(value, Collectors.<V>toList());
		Collector<? super E, ?, Sequence<V>> c2 = Collectors.collectingAndThen(c1, l -> of(l::stream));
		return stream().collect(Collectors.groupingBy(key, c2));
	}

	default Optional<E> head() {
		return stream().findFirst();
	}

	default boolean isEmpty() {
		return size() == 0;
	}

	@Override
	default Iterator<E> iterator() {
		return stream().iterator();
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
