package org.ddd4j.value;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing;
import org.ddd4j.util.value.Value;
import org.ddd4j.value.collection.Seq;

/**
 * Like {@link Optional}, but allows the use of null as value.
 *
 * @author sven.linstaedt
 */
@FunctionalInterface
public interface Opt<T> {

	class Some<T> extends Value.Simple<Some<T>, T> implements Opt<T> {

		private final T value;

		public Some(T value) {
			this.value = Require.nonNull(value);
		}

		@Override
		public <X> X applyNullable(Function<? super T, ? extends X> nullable, Supplier<? extends X> empty) {
			return nullable.apply(value);
		}

		@Override
		protected T value() {
			return value;
		}
	}

	@SuppressWarnings("rawtypes")
	Opt NONE = new Opt() {

		@Override
		public Object applyNullable(Function nullable, Supplier empty) {
			return empty.get();
		}
	};

	@SuppressWarnings("rawtypes")
	Opt NULL = new Opt() {

		@Override
		@SuppressWarnings("unchecked")
		public Object applyNullable(Function nullable, Supplier empty) {
			return nullable.apply(null);
		}
	};

	static <T> Opt<T> none() {
		return ofNone();
	}

	@SuppressWarnings("unchecked")
	static <T> Opt<T> ofNone() {
		return NONE;
	}

	static <T> Opt<T> ofNullable(T element) {
		return element != null ? of(element) : ofNull();
	}

	static <T> Opt<T> of(T element) {
		return new Some<>(element);
	}

	@SuppressWarnings("unchecked")
	static <T> Opt<T> ofNull() {
		return NULL;
	}

	static <T> Supplier<Opt<T>> wrap(Supplier<? extends T> supplier) {
		return () -> ofNullable(supplier.get());
	}

	default <X> X apply(Function<? super T, ? extends X> nonNull, Supplier<? extends X> nill, Supplier<? extends X> empty) {
		return applyNullable(t -> t != null ? nonNull.apply(t) : nill.get(), empty);
	}

	default <X> X applyNonNull(Function<? super T, ? extends X> nonNull) {
		return applyNullable(t -> t != null ? nonNull.apply(t) : null, Throwing.of(NoSuchElementException::new).asSupplier());
	}

	default <X> X applyNonNull(Function<? super T, ? extends X> nonNull, Supplier<? extends X> empty) {
		return applyNullable(t -> t != null ? nonNull.apply(t) : null, empty);
	}

	default <X> X applyNullable(Function<? super T, ? extends X> nullable) {
		return applyNullable(nullable, Throwing.of(NoSuchElementException::new).asSupplier());
	}

	<X> X applyNullable(Function<? super T, ? extends X> nullable, Supplier<? extends X> empty);

	default <X> Opt<X> cast(Class<X> type) {
		return filterNonNull(type::isInstance).mapNonNull(type::cast);
	}

	default boolean checkEqual(Object value) {
		return test(t -> Objects.deepEquals(t, value), value == null);
	}

	default boolean equal(Opt<T> other) {
		return test(other::checkEqual, other.isNull(), other.isEmpty());
	}

	default Seq<T> fillNonNull(Seq<T> seq, Predicate<? super Seq<T>> predicate) {
		return apply(t -> predicate.test(seq) ? seq.append().entry(t) : seq, () -> seq, () -> seq);
	}

	default Seq<T> fillNullable(Seq<T> seq, Predicate<? super Seq<T>> predicate) {
		return applyNullable(t -> predicate.test(seq) ? seq.append().entry(t) : seq, () -> seq);
	}

	default Opt<T> filterNonNull() {
		return filterNullable(Objects::nonNull);
	}

	default Opt<T> filterNonNull(Predicate<? super T> predicate) {
		return applyNullable(t -> t != null && predicate.test(t) ? this : Opt.none(), Opt::none);
	}

	default Opt<T> filterNullable(Predicate<? super T> predicate) {
		return applyNullable(t -> predicate.test(t) ? this : Opt.none(), Opt::none);
	}

	default <X> Opt<X> flatMap(Function<? super T, Opt<X>> nonNull, Supplier<Opt<X>> nill) {
		return apply(nonNull, nill, Opt::none);
	}

	default <X> Opt<X> flatMap(Function<? super T, Opt<X>> nonNull, Supplier<Opt<X>> nill, Supplier<Opt<X>> empty) {
		return apply(nonNull, nill, empty);
	}

	default <X> Opt<X> flatMapNonNull(Function<? super T, Opt<X>> nonNull) {
		return apply(nonNull, Opt::ofNull, Opt::none);
	}

	default <X> Opt<X> flatMapNullable(Function<? super T, Opt<X>> nullable) {
		return applyNullable(nullable, Opt::none);
	}

	default <X> Opt<X> flatMapNullable(Function<? super T, Opt<X>> nullable, Supplier<Opt<X>> empty) {
		return applyNullable(nullable, empty);
	}

	default <X> Optional<X> flatMapOptional(Function<? super T, Optional<X>> nonNull) {
		return apply(nonNull, Optional::empty, Optional::empty);
	}

	default T getEmptyAsNull() {
		return applyNullable(Function.identity(), () -> null);
	}

	default T getNonNull() {
		return apply(Function.identity(), Throwing.of(NullPointerException::new).asSupplier(),
				Throwing.of(NoSuchElementException::new).asSupplier());
	}

	default T getNullable() {
		return applyNullable(Function.identity(), Throwing.of(NoSuchElementException::new).asSupplier());
	}

	default void ifNonNullPresent(Consumer<? super T> consumer) {
		applyNonNull(t -> {
			consumer.accept(t);
			return null;
		}, () -> null);
	}

	default void ifNullablePresent(Consumer<? super T> consumer) {
		applyNullable(t -> {
			consumer.accept(t);
			return null;
		}, () -> null);
	}

	default boolean isEmpty() {
		return applyNullable(t -> false, () -> true);
	}

	default boolean isNotEmpty() {
		return !isEmpty();
	}

	default boolean isNull() {
		return applyNullable(Objects::isNull, () -> false);
	}

	default boolean isPresent() {
		return applyNullable(Objects::nonNull, () -> false);
	}

	default <X> Opt<X> map(Function<? super T, ? extends X> nonNull, Supplier<? extends X> nill) {
		return flatMap(nonNull.andThen(Opt::<X> of), wrap(nill));
	}

	default <X> Opt<X> map(Function<? super T, ? extends X> nonNull, Supplier<? extends X> nill, Supplier<? extends X> empty) {
		return flatMap(nonNull.andThen(Opt::of), wrap(nill), wrap(empty));
	}

	default <X> Opt<X> mapNonNull(Function<? super T, ? extends X> nonNull) {
		return flatMapNonNull(nonNull.andThen(Opt::<X> of));
	}

	default <X> Opt<X> mapNullable(Function<? super T, ? extends X> nullable) {
		return flatMapNullable(nullable.andThen(Opt::<X> ofNullable));
	}

	default <X> Opt<X> mapNullable(Function<? super T, ? extends X> nullable, Opt<X> empty) {
		Require.nonNull(empty);
		return flatMapNullable(nullable.andThen(Opt::ofNullable), () -> empty);
	}

	default <X> Opt<X> mapNullable(Function<? super T, ? extends X> nullable, Supplier<? extends X> empty) {
		return flatMapNullable(nullable.andThen(Opt::<X> ofNullable), wrap(empty));
	}

	default <X> Optional<X> mapOptional(Function<? super T, ? extends X> nonNull) {
		return flatMapOptional(nonNull.andThen(Optional::<X> of));
	}

	default T orElse(T other) {
		return applyNullable(Function.identity(), () -> other);
	}

	default T orElseGet(Supplier<? extends T> other) {
		return applyNullable(Function.identity(), other);
	}

	default Opt<T> orElseMap(Opt<T> other) {
		return mapNullable(Function.identity(), other);
	}

	default Opt<T> orElseMapGet(Supplier<Opt<T>> other) {
		return flatMapNullable(Opt::ofNullable, other);
	}

	default boolean test(Predicate<? super T> predicate, boolean ifNull) {
		return test(predicate, ifNull, false);
	}

	default boolean test(Predicate<? super T> predicate, boolean ifNull, boolean ifEmpty) {
		return apply(predicate::test, () -> ifNull, () -> ifEmpty);
	}

	default boolean testNonNull(Predicate<? super T> predicate) {
		return test(predicate, false, false);
	}

	default boolean testNonNull(Predicate<? super T> predicate, boolean ifEmpty) {
		return test(predicate, false, ifEmpty);
	}

	default boolean testNullable(Predicate<? super T> predicate) {
		return testNullable(predicate, false);
	}

	default boolean testNullable(Predicate<? super T> predicate, boolean ifEmpty) {
		return applyNullable(predicate::test, () -> ifEmpty);
	}

	default Optional<T> toOptional() {
		return Optional.ofNullable(getEmptyAsNull());
	}

	default Opt<T> visitNonNull(Consumer<? super T> consumer) {
		return visitNonNull(consumer, () -> {
		});
	}

	default Opt<T> visitNonNull(Consumer<? super T> consumer, Runnable empty) {
		return apply(t -> {
			consumer.accept(t);
			return this;
		}, () -> this, () -> {
			empty.run();
			return this;
		});
	}

	default Opt<T> visitNullable(Consumer<? super T> consumer) {
		return visitNullable(consumer, () -> {
		});
	}

	default Opt<T> visitNullable(Consumer<? super T> consumer, Runnable empty) {
		return applyNullable(t -> {
			consumer.accept(t);
			return this;
		}, () -> {
			empty.run();
			return this;
		});
	}
}
