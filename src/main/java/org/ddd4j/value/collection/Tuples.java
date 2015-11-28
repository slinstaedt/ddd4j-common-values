package org.ddd4j.value.collection;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.ddd4j.value.Either;

@FunctionalInterface
public interface Tuples<L, R> {

	static <K, V> Tuples<K, V> ofCollection(Collection<Tpl<K, V>> col) {
		return f -> f.apply((k) -> col.stream().filter(t -> t.testLeft(k::equals)).findFirst().get(),
				(k, v) -> Tpl.of(k, map.put(k, v)));
	}

	static <K, V> Tuples<K, V> ofMap(Map<K, V> map) {
		return f -> f.apply((k) -> Tpl.of(k, map.get(k)), (k, v) -> Tpl.of(k, map.put(k, v)));
	}

	default Tpl<L, R> add(L left, R right) {
		return apply((g, s) -> s.apply(left, right));
	}

	Tpl<L, R> apply(BiFunction<Function<Either<L, R>, Tpl<L, R>>, Function<Tpl<L, R>, Tpl<L, R>>, Tpl<L, R>> f);

	default boolean containsLeft(L value) {
		return ofLeft(value) != null;
	}

	default boolean containsRight(R value) {
		return ofRight(value) != null;
	}

	default Tuples<L, R> index() {
		return ofMap(toMap());
	}

	default Tuples<R, L> inverse() {
		// TODO
		return c -> null;
	}

	default Tpl<L, R> ofLeft(L value) {
		return apply((g, s) -> g.apply(Either.left(value)));
	}

	default Tpl<L, R> ofRight(R value) {
		return apply((g, s) -> g.apply(Either.right(value)));
	}

	default Map<L, R> toMap() {
		Map<L, R> map = new HashMap<>();
		for (Tpl<L, R> tpl : tuples()) {
			tpl.fold((k, v) -> map.put(k, v));
		}
		return map;
	}

	default Collection<Tpl<L, R>> tuples() {

	}
}
