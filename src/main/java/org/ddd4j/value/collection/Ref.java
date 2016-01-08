package org.ddd4j.value.collection;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@FunctionalInterface
public interface Ref<T> {

	static <T> Ref<T> create() {
		@SuppressWarnings("unchecked")
		T[] holder = (T[]) new Object[1];
		return Ref.of(holder, h -> h[0], (h, e) -> h[0] = e);
	}

	static <T> Ref<T> createThreadsafe() {
		AtomicReference<T> holder = new AtomicReference<>();
		return f -> Tpl.of(holder.get(), holder.updateAndGet(f));
	}

	static <T, H> Supplier<Ref<T>> factoryOf(Supplier<H> factory, Function<? super H, ? extends T> getter, BiConsumer<? super H, ? super T> setter) {
		requireNonNull(factory);
		requireNonNull(getter);
		requireNonNull(setter);
		return () -> of(factory.get(), getter, setter);
	}

	static <T, H> Ref<T> of(H holder, Function<? super H, ? extends T> getter, BiConsumer<? super H, ? super T> setter) {
		requireNonNull(holder);
		requireNonNull(getter);
		requireNonNull(setter);
		return of(() -> getter.apply(holder), t -> setter.accept(holder, t));
	}

	static <T> Ref<T> of(Supplier<? extends T> getter, Consumer<? super T> setter) {
		requireNonNull(getter);
		requireNonNull(setter);
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

	default T get() {
		return getAndUpdate(UnaryOperator.identity());
	}

	default T getAndUpdate(Supplier<? extends T> supplier) {
		return getAndUpdate(t -> supplier.get());
	}

	default T getAndUpdate(Supplier<? extends T> supplier, Predicate<? super T> condition) {
		return getAndUpdate(t -> supplier.get(), condition);
	}

	default T getAndUpdate(T newValue) {
		return getAndUpdate(t -> newValue);
	}

	default T getAndUpdate(T newValue, Predicate<? super T> condition) {
		return getAndUpdate(t -> newValue, condition);
	}

	default T getAndUpdate(UnaryOperator<T> updateFunction) {
		return update(updateFunction).foldLeft(Function.identity());
	}

	default T getAndUpdate(UnaryOperator<T> updateFunction, Predicate<? super T> condition) {
		return update(updateFunction, condition).foldLeft(Function.identity());
	}

	default Ref<T> set(T value) {
		getAndUpdate(t -> value);
		return this;
	}

	default Tpl<T, T> update(Supplier<? extends T> supplier) {
		return update(t -> supplier.get());
	}

	default Tpl<T, T> update(Supplier<? extends T> supplier, Predicate<? super T> condition) {
		return update(t -> supplier.get(), condition);
	}

	default Tpl<T, T> update(T value) {
		return update(t -> value);
	}

	default Tpl<T, T> update(T value, Predicate<? super T> condition) {
		return update(t -> value, condition);
	}

	Tpl<T, T> update(UnaryOperator<T> updateFunction);

	default Tpl<T, T> update(UnaryOperator<T> updateFunction, Predicate<? super T> condition) {
		return update(t -> condition.test(t) ? updateFunction.apply(t) : t);
	}

	default T updateAndGet(Supplier<? extends T> supplier) {
		return updateAndGet(t -> supplier.get());
	}

	default T updateAndGet(Supplier<? extends T> supplier, Predicate<? super T> condition) {
		return updateAndGet(t -> supplier.get(), condition);
	}

	default T updateAndGet(T value) {
		return updateAndGet(t -> value);
	}

	default T updateAndGet(T value, Predicate<? super T> condition) {
		return updateAndGet(t -> value, condition);
	}

	default T updateAndGet(UnaryOperator<T> updateFunction) {
		return update(updateFunction).foldRight(Function.identity());
	}

	default T updateAndGet(UnaryOperator<T> updateFunction, Predicate<? super T> condition) {
		return update(updateFunction, condition).foldRight(Function.identity());
	}
}
