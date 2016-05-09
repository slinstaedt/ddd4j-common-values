package org.ddd4j.value.collection;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.ddd4j.contract.Require;

@FunctionalInterface
public interface Ref<T> {

	@FunctionalInterface
	interface RefOpt<T> extends Ref<Opt<T>> {

		static <T> RefOpt<T> create(Opt<T> initial) {
			return Ref.<Opt<T>> create().set(Require.nonNull(initial))::update;
		}

		default Ref<T> asNonEmpty() {
			return f -> update(o -> o.mapNullable(f)).map(Opt::getNullable, Opt::getNullable);
		}

		default Opt<T> empty() {
			return update(Opt.empty()).getLeft();
		}

		default T emptyIf(boolean condition) {
			return emptyIf(t -> condition);
		}

		default T emptyIf(Predicate<? super T> predicate) {
			return update(Opt.empty(), o -> o.testNullable(predicate)).foldLeft(Opt::getEmptyAsNull);
		}

		default T getNullable() {
			return get().getNullable();
		}

		default boolean isEmpty() {
			return get().isEmpty();
		}

		default boolean isNotNull() {
			return get().isNotNull();
		}
	}

	static <T> Ref<T> create() {
		@SuppressWarnings("unchecked")
		T[] holder = (T[]) new Object[1];
		return of(holder, h -> h[0], (h, e) -> h[0] = e);
	}

	static <T> Ref<T> createThreadsafe() {
		AtomicReference<T> holder = new AtomicReference<>();
		return f -> Tpl.of(holder.get(), holder.updateAndGet(f));
	}

	static <T, H> Supplier<Ref<T>> factoryOf(Supplier<H> factory, Function<? super H, ? extends T> getter, BiConsumer<? super H, ? super T> setter) {
		Require.nonNullElements(factory, getter, setter);
		return () -> of(factory.get(), getter, setter);
	}

	static <T, H> Ref<T> of(H holder, Function<? super H, ? extends T> getter, BiConsumer<? super H, ? super T> setter) {
		Require.nonNullElements(holder, getter, setter);
		return of(() -> getter.apply(holder), t -> setter.accept(holder, t));
	}

	static <T> Ref<T> of(Supplier<? extends T> getter, Consumer<? super T> setter) {
		Require.nonNullElements(getter, setter);
		return f -> {
			T oldValue = getter.get();
			T newValue = f.apply(oldValue);
			setter.accept(newValue);
			return Tpl.of(oldValue, newValue);
		};
	}

	static <T> Ref<T> of(T value) {
		return Ref.<T> create().set(value);
	}

	default <X> X apply(Function<? super T, X> mapper) {
		return mapper.apply(get());
	}

	default T get() {
		return getAndUpdate(UnaryOperator.identity());
	}

	default T getAndUpdate(Supplier<? extends T> supplier) {
		return getAndUpdate(t -> supplier.get());
	}

	default T getAndUpdate(Supplier<? extends T> supplier, Predicate<? super T> predicate) {
		return getAndUpdate(t -> supplier.get(), predicate);
	}

	default T getAndUpdate(T newValue) {
		return getAndUpdate(t -> newValue);
	}

	default T getAndUpdate(T newValue, Predicate<? super T> predicate) {
		return getAndUpdate(t -> newValue, predicate);
	}

	default T getAndUpdate(UnaryOperator<T> updateFunction) {
		return update(updateFunction).foldLeft(Function.identity());
	}

	default T getAndUpdate(UnaryOperator<T> updateFunction, Predicate<? super T> predicate) {
		return update(updateFunction, predicate).foldLeft(Function.identity());
	}

	default <X> UnaryOperator<X> mapTo(BiFunction<X, ? super T, X> mapper) {
		Require.nonNull(mapper);
		return x -> mapper.apply(x, get());
	}

	default Ref<T> set(T value) {
		getAndUpdate(t -> value);
		return this;
	}

	default Tpl<T, T> update(Supplier<? extends T> supplier) {
		return update(t -> supplier.get());
	}

	default Tpl<T, T> update(Supplier<? extends T> supplier, Predicate<? super T> predicate) {
		return update(t -> supplier.get(), predicate);
	}

	default Tpl<T, T> update(T value) {
		return update(t -> value);
	}

	default Tpl<T, T> update(T value, Predicate<? super T> predicate) {
		return update(t -> value, predicate);
	}

	/**
	 * Updates this reference with the given function. Returns a {@link Tpl} with left containing the old value and right the new one.
	 *
	 * @param updateFunction
	 *            The function to update this reference with
	 * @return Left contains old value, right the new one
	 */
	Tpl<T, T> update(UnaryOperator<T> updateFunction);

	default Tpl<T, T> update(UnaryOperator<T> updateFunction, Predicate<? super T> predicate) {
		return update(t -> predicate.test(t) ? updateFunction.apply(t) : t);
	}

	default T updateAndGet(Supplier<? extends T> supplier) {
		return updateAndGet(t -> supplier.get());
	}

	default T updateAndGet(Supplier<? extends T> supplier, Predicate<? super T> predicate) {
		return updateAndGet(t -> supplier.get(), predicate);
	}

	default T updateAndGet(T value) {
		return updateAndGet(t -> value);
	}

	default T updateAndGet(T value, Predicate<? super T> predicate) {
		return updateAndGet(t -> value, predicate);
	}

	default T updateAndGet(UnaryOperator<T> updateFunction) {
		return update(updateFunction).foldRight(Function.identity());
	}

	default T updateAndGet(UnaryOperator<T> updateFunction, Predicate<? super T> predicate) {
		return update(updateFunction, predicate).foldRight(Function.identity());
	}
}
