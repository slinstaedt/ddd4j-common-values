package org.ddd4j.value;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

@FunctionalInterface
public interface Throwing {

	@FunctionalInterface
	interface TBiFunction<T, U, R> extends BiFunction<T, U, R> {

		static <T, U, R> BiFunction<T, U, R> of(TBiFunction<T, U, R> function) {
			return requireNonNull(function)::apply;
		}

		@Override
		default R apply(T t, U u) {
			try {
				return applyChecked(t, u);
			} catch (Throwable e) {
				return Throwing.unchecked(e);
			}
		}

		R applyChecked(T t, U u) throws Throwable;
	}

	@FunctionalInterface
	interface TFunction<T, R> extends Function<T, R> {

		static <T, R> Function<T, R> of(TFunction<T, R> function) {
			return requireNonNull(function)::apply;
		}

		@Override
		default R apply(T t) {
			try {
				return applyChecked(t);
			} catch (Throwable e) {
				return Throwing.unchecked(e);
			}
		}

		R applyChecked(T t) throws Throwable;
	}

	@FunctionalInterface
	interface TSupplier<T> extends Supplier<T> {

		static <T> Supplier<T> of(TSupplier<T> supplier) {
			return requireNonNull(supplier)::get;
		}

		@Override
		default T get() {
			try {
				return getChecked();
			} catch (Throwable e) {
				return Throwing.unchecked(e);
			}
		}

		T getChecked() throws Throwable;
	}

	String EXCEPTION_MESSAGE_TEMPLATE = "Could not invoke this with arguments %s";

	@SuppressWarnings("unchecked")
	static <X, E extends Throwable> X any(Throwable throwable) throws E {
		requireNonNull(throwable);
		throw (E) throwable;
	}

	static Throwing of(Function<? super String, ? extends Throwable> exceptionFactory) {
		return requireNonNull(exceptionFactory)::apply;
	}

	static <X> X unchecked(Throwable throwable) {
		return Throwing.<X, RuntimeException> any(throwable);
	}

	default <T, U, R> TBiFunction<T, U, R> asBiFunction() {
		return (t, u) -> throwChecked(t, u);
	}

	default <T, R> TFunction<T, R> asFunction() {
		return (t) -> throwChecked(t);
	}

	default <T> TSupplier<T> asSupplier() {
		return () -> throwChecked();
	}

	Throwable createThrowable(String message);

	default String formatMessage(Object... args) {
		return Arrays.asList(args).toString();
	}

	default <X> X throwChecked(Object... args) throws Throwable {
		return any(createThrowable(formatMessage(args)));
	}

	default <X> X throwUnchecked(Object... args) {
		return unchecked(createThrowable(formatMessage(args)));
	}

	default Throwing withMessage(Function<Object[], String> messageFormatter) {
		return new Throwing() {

			@Override
			public Throwable createThrowable(String message) {
				return Throwing.this.createThrowable(message);
			}

			@Override
			public String formatMessage(Object... args) {
				return messageFormatter.apply(args);
			}
		};
	}
}
