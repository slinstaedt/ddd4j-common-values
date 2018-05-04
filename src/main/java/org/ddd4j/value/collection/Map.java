package org.ddd4j.value.collection;

import java.util.Collection;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing;
import org.ddd4j.value.Opt;

@FunctionalInterface
public interface Map<K, V> extends Seq<Tpl<K, V>> {

	@FunctionalInterface
	interface MapFunction<K, V> {

		Tpl<Map<K, V>, Opt<V>> apply(Function<K, Opt<V>> read, BiFunction<K, V, Tpl<Map<K, V>, Opt<V>>> write, Supplier<Stream<Tpl<K, V>>> stream);
	}

	static <K, V> Map<K, V> ofCollection(Collection<Tpl<K, V>> collection) {
		// TODO
		return null;
	}

	static <K, V> Map<K, V> empty() {
		return wrap(new HashMap<>(), HashMap<K, V>::new);
	}

	static <K, V> Map<K, V> wrap(java.util.Map<K, V> map, UnaryOperator<java.util.Map<K, V>> copyFactory) {
		Require.nonNulls(map, copyFactory);
		return f -> f.apply(k -> valueOf(map, k), (k, v) -> {
			Opt<V> value = valueOf(map, k);
			java.util.Map<K, V> copy = copyFactory.apply(map);
			copy.put(k, v);
			return Tpl.of(wrap(copy, copyFactory), value);
		}, () -> map.entrySet().stream().map(e -> Tpl.of(e.getKey(), e.getValue())));
	}

	static <K, V> Opt<V> valueOf(java.util.Map<K, V> map, K key) {
		V value;
		return (((value = map.get(key)) != null) || map.containsKey(key)) ? Opt.of(value) : Opt.none();
	}

	Tpl<Map<K, V>, Opt<V>> apply(MapFunction<K, V> function);

	default Opt<V> get(K key) {
		return apply((r, w, s) -> Tpl.of(this, r.apply(key))).getRight();
	}

	default Tpl<Map<K, V>, Opt<V>> put(K key, V value) {
		return apply((r, w, s) -> w.apply(key, value));
	}

	default Map<K, V> with(K key, V value) {
		return put(key, value).fold((m, o) -> o.applyNullable(Throwing.of(IllegalArgumentException::new).asFunction(), () -> m));
	}

	@Override
	default Stream<Tpl<K, V>> stream() {
		// TODO
		return null;
	}

	default Tpl<Map<K, V>, V> computeIfAbsent(K key, V elseValue) {
		return get(key).mapNullable(v -> Tpl.of(this, v)).orElseGet(() -> Tpl.of(put(key, elseValue).getLeft(), elseValue));
	}

	default V getOrElse(K key, V elseValue) {
		return get(key).orElse(elseValue);
	}

	default Map<K, V> updated(K key, V value) {
		return put(key, value).getLeft();
	}

	default boolean containsKey(K key) {
		return get(key).isNotEmpty();
	}
}
