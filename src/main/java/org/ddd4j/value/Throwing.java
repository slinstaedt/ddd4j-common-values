package org.ddd4j.value;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.ddd4j.contract.Require;

@FunctionalInterface
public interface Throwing {

	@FunctionalInterface
	interface TBiFunction<T, U, R> extends BiFunction<T, U, R> {

		@Override
		default R apply(T t, U u) {
			try {
				return applyChecked(t, u);
			} catch (Throwable e) {
				return Throwing.unchecked(e);
			}
		}

		R applyChecked(T t, U u) throws Throwable;

		default BiFunction<T, U, Either<R, Throwable>> asEither() {
			return (t, u) -> {
				try {
					return Either.left(applyChecked(t, u));
				} catch (Throwable e) {
					return Either.right(e);
				}
			};
		}

		default BiFunction<T, U, Opt<R>> asOptional() {
			return (t, u) -> {
				try {
					return Opt.of(applyChecked(t, u));
				} catch (Throwable e) {
					return Opt.none();
				}
			};
		}
	}

	@FunctionalInterface
	interface TConsumer<T> extends Consumer<T> {

		@Override
		default void accept(T t) {
			try {
				acceptChecked(t);
			} catch (Throwable e) {
				Throwing.unchecked(e);
			}
		}

		default TConsumer<T> ignoreExceptions() {
			return ignore(Throwable.class);
		}

		default TConsumer<T> ignore(Class<? extends Throwable> exceptionType) {
			return t -> {
				try {
					acceptChecked(t);
				} catch (Throwable e) {
					if (!exceptionType.isInstance(e)) {
						Throwing.unchecked(e);
					}
				}
			};
		}

		default TPredicate<T> catching(Class<? extends Throwable> exceptionType) {
			return t -> {
				try {
					acceptChecked(t);
					return true;
				} catch (Throwable e) {
					return exceptionType.isInstance(e) ? false : Throwing.unchecked(e);
				}
			};
		}

		default TPredicate<T> catchingExceptions() {
			return catching(Throwable.class);
		}

		void acceptChecked(T t) throws Throwable;
	}

	@FunctionalInterface
	interface TFunction<T, R> extends Function<T, R> {

		@Override
		default R apply(T t) {
			try {
				return applyChecked(t);
			} catch (Throwable e) {
				return Throwing.unchecked(e);
			}
		}

		R applyChecked(T t) throws Throwable;

		default Function<T, Either<R, Throwable>> asEither() {
			return t -> {
				try {
					return Either.left(applyChecked(t));
				} catch (Throwable e) {
					return Either.right(e);
				}
			};
		}

		default Function<T, Opt<R>> asOptional() {
			return t -> {
				try {
					return Opt.of(applyChecked(t));
				} catch (Throwable e) {
					return Opt.none();
				}
			};
		}
	}

	@FunctionalInterface
	interface TRunnable extends Runnable {

		void runChecked() throws Throwable;

		@Override
		default void run() {
			try {
				runChecked();
			} catch (Throwable e) {
				Throwing.unchecked(e);
			}
		}
	}

	@FunctionalInterface
	interface TPredicate<T> extends Predicate<T> {

		boolean testChecked(T t) throws Throwable;

		@Override
		default boolean test(T t) {
			try {
				return testChecked(t);
			} catch (Throwable e) {
				return Throwing.unchecked(e);
			}
		}

		default TPredicate<T> returningFalseOn(Class<? extends Throwable> exceptionType) {
			return t -> {
				try {
					return testChecked(t);
				} catch (Throwable e) {
					return exceptionType.isInstance(e) ? false : Throwing.unchecked(e);
				}
			};
		}

		default TPredicate<T> returningFalseOnException() {
			return returningFalseOn(Throwable.class);
		}
	}

	@FunctionalInterface
	interface TSupplier<T> extends Supplier<T> {

		default Supplier<Either<T, Throwable>> asEither() {
			return () -> {
				try {
					return Either.left(getChecked());
				} catch (Throwable e) {
					return Either.right(e);
				}
			};
		}

		default Supplier<Opt<T>> asOptional() {
			return () -> {
				try {
					return Opt.of(getChecked());
				} catch (Throwable e) {
					return Opt.none();
				}
			};
		}

		default <X> TSupplier<X> map(TFunction<? super T, X> mapper) {
			return () -> mapper.applyChecked(getChecked());
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
		Require.nonNull(throwable);
		throw (E) throwable;
	}

	static Throwing of(Function<? super String, ? extends Throwable> exceptionFactory) {
		return Require.nonNull(exceptionFactory)::apply;
	}

	static <T, R> TFunction<T, R> ofApplied(TFunction<T, R> function) {
		return Require.nonNull(function);
	}

	static <T, U, R> TBiFunction<T, U, R> ofApplied(TBiFunction<T, U, R> function) {
		return Require.nonNull(function);
	}

	static <T> TConsumer<T> ofConsumed(TConsumer<T> consumer) {
		return Require.nonNull(consumer);
	}

	static TRunnable ofRunning(TRunnable runnable) {
		return Require.nonNull(runnable);
	}

	static <T> TSupplier<T> ofSupplied(TSupplier<T> supplier) {
		return Require.nonNull(supplier);
	}

	static <T> TPredicate<T> ofTested(TPredicate<T> predicate) {
		return Require.nonNull(predicate);
	}

	static <X> X unchecked(Throwable throwable) {
		return Throwing.<X, RuntimeException>any(throwable);
	}

	default <T, U, R> TBiFunction<T, U, R> asBiFunction() {
		return (t, u) -> throwChecked(t, u);
	}

	default <T> TConsumer<T> asConsumer() {
		return (t) -> throwChecked(t);
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
