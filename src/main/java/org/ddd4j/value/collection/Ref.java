package org.ddd4j.value.collection;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Opt;

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
			return emptyIf(true);
		}

		default Opt<T> emptyIf(boolean condition) {
			return emptyIf(t -> condition);
		}

		default Opt<T> emptyIf(Predicate<? super T> predicate) {
			return updateWithValue(Opt.none(), o -> o.testNullable(predicate)).getLeft();
		}

		default T getNullable() {
			return get().getNullable();
		}

		default boolean isEmpty() {
			return get().isEmpty();
		}

		default boolean isNotEmpty() {
			return !isEmpty();
		}

		default boolean isPresent() {
			return get().isPresent();
		}
	}

	@FunctionalInterface
	interface RefTpl<L, R> extends Ref<Tpl<L, R>> {

		static <L, R> RefTpl<L, R> create(L left, R right) {
			return Ref.<Tpl<L, R>> create().set(Tpl.of(left, right))::update;
		}

		default <T> T fold(BiFunction<? super L, ? super R, ? extends T> function) {
			return apply(tpl -> tpl.fold(function));
		}

		default L getLeft() {
			return get().getLeft();
		}

		default R getRight() {
			return get().getRight();
		}

		default void setLeft(L left) {
			updateLeft(l -> left);
		}

		default void setRight(R right) {
			updateRight(r -> right);
		}

		default L updateLeft(UnaryOperator<L> mapper) {
			return update(tpl -> tpl.mapLeft(mapper).flatten()).foldLeft(Tpl::getLeft);
		}

		default void updateRight(UnaryOperator<R> mapper) {
			update(tpl -> tpl.mapRight(mapper).flatten()).foldLeft(Tpl::getRight);
		}
	}

	static <T> Ref<T> create() {
		@SuppressWarnings("unchecked")
		T[] holder = (T[]) new Object[1];
		return of(holder, h -> h[0], (h, e) -> h[0] = e);
	}

	static <T> Ref<T> create(T initial) {
		return Ref.<T> create().set(initial);
	}

	static <T> Ref<T> createThreadsafe() {
		AtomicReference<T> holder = new AtomicReference<>();
		return f -> Tpl.of(holder.get(), holder.updateAndGet(f));
	}

	static <T, H> Supplier<Ref<T>> factoryOf(Supplier<H> factory, Function<? super H, ? extends T> getter,
			BiConsumer<? super H, ? super T> setter) {
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

	default T getAndUpdate(UnaryOperator<T> updateFunction) {
		return update(updateFunction).foldLeft(Function.identity());
	}

	default T getAndUpdate(UnaryOperator<T> updateFunction, Predicate<? super T> predicate) {
		return update(updateFunction, predicate).foldLeft(Function.identity());
	}

	default T getAndUpdateWithValue(T newValue) {
		return getAndUpdate(t -> newValue);
	}

	default T getAndUpdateWithValue(T newValue, Predicate<? super T> predicate) {
		return getAndUpdate(t -> newValue, predicate);
	}

	default Ref<T> ifNotPresent(Runnable runnable) {
		update(t -> {
			runnable.run();
			return t;
		}, Objects::isNull);
		return this;
	}

	default Ref<T> ifPresent(Consumer<? super T> consumer) {
		update(t -> {
			consumer.accept(t);
			return t;
		}, Objects::nonNull);
		return this;
	}

	default boolean isNull() {
		return get() == null;
	}

	default Ref<T> set(T value) {
		getAndUpdate(t -> value);
		return this;
	}

	default Ref<T> setOptional(Opt<T> value) {
		return value.applyNullable(this::set, () -> this);
	}

	default Ref<T> unset() {
		return set(null);
	}

	default Tpl<T, T> update(Supplier<? extends T> supplier) {
		return update(t -> supplier.get());
	}

	default Tpl<T, T> update(Supplier<? extends T> supplier, Predicate<? super T> predicate) {
		return update(t -> supplier.get(), predicate);
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

	default T updateAndGet(UnaryOperator<T> updateFunction) {
		return update(updateFunction).foldRight(Function.identity());
	}

	default T updateAndGet(UnaryOperator<T> updateFunction, Predicate<? super T> predicate) {
		return update(updateFunction, predicate).foldRight(Function.identity());
	}

	default T updateAndGetWithValue(T value) {
		return updateAndGet(t -> value);
	}

	default T updateAndGetWithValue(T value, Predicate<? super T> predicate) {
		return updateAndGet(t -> value, predicate);
	}

	default Tpl<T, T> updateWithValue(T value) {
		return update(t -> value);
	}

	default Tpl<T, T> updateWithValue(T value, Predicate<? super T> predicate) {
		return update(t -> value, predicate);
	}
}
