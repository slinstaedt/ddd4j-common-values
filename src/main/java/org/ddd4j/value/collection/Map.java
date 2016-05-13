package org.ddd4j.value.collection;

import java.util.Collection;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

@FunctionalInterface
public interface Map<K, V> extends Seq<Tpl<K, V>> {

	static <K, V> Map<K, V> ofCollection(Collection<Tpl<K, V>> col) {
		return null;
	}

	static <K, V> Map<K, V> create() {
		return wrap(HashMap<K, V>::new);
	}

	static <K, V> Map<K, V> wrap(UnaryOperator<java.util.Map<K, V>> copyFactory) {
		return rw -> rw.apply(map::get, (k, v) -> {
		});
	}

	Map<K, V> apply(BiFunction<Function<? super K, ? extends V>, BiFunction<? super K, ? super V, Tpl<Map<K, V>, V>>, Tpl<Map<K, V>, V>> rw);

	default V get(K key) {
		return apply((r, w) -> r.apply(key)).getRight();
	}

	default Tpl<Map<K, V>, V> put(K key, V value) {
		return apply((r, w) -> w.apply(key, value));
	}

	@Override
	default Stream<Tpl<K, V>> stream() {
		// TODO Auto-generated method stub
		return null;
	}
}
