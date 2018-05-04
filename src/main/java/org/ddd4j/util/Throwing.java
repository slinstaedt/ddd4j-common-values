package org.ddd4j.util;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.ddd4j.value.Either;
import org.ddd4j.value.Opt;

//TODO remove "T" prefix from all inner types?
//TODO rename to java function types
@FunctionalInterface
public interface Throwing {

	@FunctionalInterface
	interface Closeable extends AutoCloseable {

		@Override
		default void close() {
			try {
				closeChecked();
			} catch (Exception e) {
				Throwing.unchecked(e);
			}
		};

		void closeChecked() throws Exception;
	}

	@FunctionalInterface
	interface Producer<T> extends java.util.function.Supplier<T>, Callable<T> {

		static <T> Producer<T> of(Producer<T> producer) {
			return Require.nonNull(producer);
		}

		default Producer<Either<T, Exception>> asEither() {
			return () -> {
				try {
					return Either.left(produce());
				} catch (Exception e) {
					return Either.right(e);
				}
			};
		}

		default Producer<Opt<T>> asOptional() {
			return () -> {
				try {
					return Opt.of(produce());
				} catch (Exception e) {
					return Opt.none();
				}
			};
		}

		@Override
		default T call() throws Exception {
			return produce();
		}

		@Override
		default T get() {
			try {
				return produce();
			} catch (Exception e) {
				return Throwing.unchecked(e);
			}
		}

		default <X> Producer<X> map(Function<? super T, ? extends X> mapper) {
			return () -> mapper.apply(produce());
		}

		T produce() throws Exception;

		default Producer<T> withListener(TConsumer<? super T> success, TConsumer<? super Throwable> failure) {
			Require.nonNulls(success, failure);
			return () -> {
				try {
					T value = produce();
					success.acceptChecked(value);
					return value;
				} catch (Throwable e) {
					failure.acceptChecked(e);
					throw e;
				}
			};
		}
	}

	@FunctionalInterface
	interface Task extends Runnable {

		Task NONE = () -> {
		};

		static Task of(Task task) {
			return Require.nonNull(task);
		}

		default <T> Producer<T> andThen(Producer<T> producer) {
			return () -> {
				perform();
				return producer.produce();
			};
		}

		default Task andThen(Task task) {
			return () -> {
				this.perform();
				task.perform();
			};
		}

		void perform() throws Exception;

		@Override
		default void run() {
			try {
				perform();
			} catch (Exception e) {
				Throwing.unchecked(e);
			}
		}
	}

	@FunctionalInterface
	interface TBiConsumer<T, U> extends BiConsumer<T, U> {

		@Override
		default void accept(T t, U u) {
			try {
				acceptChecked(t, u);
			} catch (Exception e) {
				Throwing.unchecked(e);
			}
		}

		void acceptChecked(T t, U u) throws Exception;

		default TBiConsumer<T, U> andThen(TBiConsumer<? super T, ? super U> consumer) {
			return (t, u) -> {
				accept(t, u);
				consumer.accept(t, u);
			};
		}
	}

	@FunctionalInterface
	interface TBiFunction<T, U, R> extends BiFunction<T, U, R> {

		static <T, U, R> TBiFunction<T, U, R> of(TBiFunction<T, U, R> function) {
			return Require.nonNull(function);
		}

		@Override
		default R apply(T t, U u) {
			try {
				return applyChecked(t, u);
			} catch (Exception e) {
				return Throwing.unchecked(e);
			}
		}

		R applyChecked(T t, U u) throws Exception;

		default BiFunction<T, U, Either<R, Exception>> asEither() {
			return (t, u) -> {
				try {
					return Either.left(applyChecked(t, u));
				} catch (Exception e) {
					return Either.right(e);
				}
			};
		}

		default BiFunction<T, U, Opt<R>> asOptional() {
			return (t, u) -> {
				try {
					return Opt.of(applyChecked(t, u));
				} catch (Exception e) {
					return Opt.none();
				}
			};
		}
	}

	@FunctionalInterface
	interface TBiPredicate<T, U> extends BiPredicate<T, U> {

		default TBiPredicate<T, U> returningFalseOn(Class<? extends Exception> exceptionType) {
			return (t, u) -> {
				try {
					return testChecked(t, u);
				} catch (Exception e) {
					return exceptionType.isInstance(e) ? false : Throwing.unchecked(e);
				}
			};
		}

		default TBiPredicate<T, U> returningFalseOnException() {
			return returningFalseOn(Exception.class);
		}

		@Override
		default boolean test(T t, U u) {
			try {
				return testChecked(t, u);
			} catch (Exception e) {
				return Throwing.unchecked(e);
			}
		}

		boolean testChecked(T t, U u) throws Exception;

		default TBiConsumer<T, U> throwOnFail(Function<String, Exception> exceptionFactory) {
			return (t, u) -> {
				if (!testChecked(t, u)) {
					throw exceptionFactory.apply("Failed test " + this + " on t=" + t + " , u=" + u);
				}
			};
		}
	}

	@FunctionalInterface
	interface TConsumer<T> extends Consumer<T> {

		static <T> TConsumer<T> of(TConsumer<T> consumer) {
			return Require.nonNull(consumer);
		}

		@Override
		default void accept(T t) {
			try {
				acceptChecked(t);
			} catch (Exception e) {
				Throwing.unchecked(e);
			}
		}

		void acceptChecked(T t) throws Exception;

		default void acceptNonNull(T t) {
			if (t != null) {
				accept(t);
			}
		}

		default TConsumer<T> andThen(Task action) {
			return andThen(t -> action.run());
		}

		default TConsumer<T> andThen(TConsumer<? super T> consumer) {
			return t -> {
				accept(t);
				consumer.accept(t);
			};
		}

		default TFunction<T, T> asFunction() {
			return t -> {
				acceptChecked(t);
				return t;
			};
		}

		default TPredicate<T> catching(Class<? extends Exception> exceptionType) {
			return t -> {
				try {
					acceptChecked(t);
					return true;
				} catch (Exception e) {
					return exceptionType.isInstance(e) ? false : Throwing.unchecked(e);
				}
			};
		}

		default TPredicate<T> catchingExceptions() {
			return catching(Exception.class);
		}

		default TConsumer<T> ignoring(Class<? extends Exception> exceptionType) {
			Require.nonNull(exceptionType);
			return t -> {
				try {
					acceptChecked(t);
				} catch (Exception e) {
					if (!exceptionType.isInstance(e)) {
						Throwing.unchecked(e);
					}
				}
			};
		}

		default TConsumer<T> ignoringExceptions() {
			return ignoring(Exception.class);
		}
	}

	@FunctionalInterface
	interface TFunction<T, R> extends Function<T, R> {

		static <T, R> TFunction<T, R> of(TFunction<T, R> function) {
			return Require.nonNull(function);
		}

		@Override
		default <V> TFunction<T, V> andThen(Function<? super R, ? extends V> after) {
			return Function.super.andThen(after)::apply;
		}

		@Override
		default R apply(T t) {
			try {
				return applyChecked(t);
			} catch (Exception e) {
				return Throwing.unchecked(e);
			}
		}

		R applyChecked(T t) throws Exception;

		default Function<T, Either<R, Exception>> asEither() {
			return t -> {
				try {
					return Either.left(applyChecked(t));
				} catch (Exception e) {
					return Either.right(e);
				}
			};
		}

		default Function<T, Opt<R>> asOptional() {
			return t -> {
				try {
					return Opt.of(applyChecked(t));
				} catch (Exception e) {
					return Opt.none();
				}
			};
		}
	}

	@FunctionalInterface
	interface TPredicate<T> extends Predicate<T> {

		static <T> TPredicate<T> of(TPredicate<T> predicate) {
			return Require.nonNull(predicate);
		}

		default TPredicate<T> returningFalseOn(Class<? extends Exception> exceptionType) {
			return t -> {
				try {
					return testChecked(t);
				} catch (Exception e) {
					return exceptionType.isInstance(e) ? false : Throwing.unchecked(e);
				}
			};
		}

		default TPredicate<T> returningFalseOnException() {
			return returningFalseOn(Exception.class);
		}

		@Override
		default boolean test(T t) {
			try {
				return testChecked(t);
			} catch (Exception e) {
				return Throwing.unchecked(e);
			}
		}

		boolean testChecked(T t) throws Exception;

		default TConsumer<T> throwOnFail(Function<String, Exception> exceptionFactory) {
			return t -> {
				if (!testChecked(t)) {
					throw exceptionFactory.apply("Failed test " + this + " on: " + t);
				}
			};
		}
	}

	String EXCEPTION_MESSAGE_TEMPLATE = "Could not invoke this with arguments %s";

	@SafeVarargs
	static <T> T[] arrayConcat(T[] array, T... append) {
		if (append.length == 0) {
			return array;
		} else {
			T[] copy = Arrays.copyOf(array, array.length + append.length);
			System.arraycopy(append, 0, copy, array.length, append.length);
			return copy;
		}
	}

	static Throwing of(Function<? super String, ? extends Throwable> exceptionFactory) {
		return Require.nonNull(exceptionFactory)::apply;
	}

	@SuppressWarnings("unchecked")
	static <X, E extends Throwable> X unchecked(Throwable throwable) throws E {
		Require.nonNull(throwable);
		throw (E) throwable;
	}

	default <T, U, R> TBiFunction<T, U, R> asBiFunction(Object... args) {
		return (t, u) -> throwChecked(arrayConcat(args, t, u));
	}

	default <T> TConsumer<T> asConsumer(Object... args) {
		return (t) -> throwChecked(arrayConcat(args, t));
	}

	default <T, R> TFunction<T, R> asFunction(Object... args) {
		return (t) -> throwChecked(arrayConcat(args, t));
	}

	default <T> Producer<T> asSupplier(Object... args) {
		return () -> throwChecked(args);
	}

	Throwable createException(String message);

	default String formatMessage(Object... args) {
		String formatted = Arrays.toString(args);
		return formatted.substring(1, formatted.length() - 1);
	}

	default <X> X throwChecked(Object... args) throws Exception {
		return unchecked(createException(formatMessage(args)));
	}

	default <X> X throwUnchecked(Object... args) {
		return unchecked(createException(formatMessage(args)));
	}

	default Throwing withMessage(Function<Object[], String> messageFormatter) {
		return new Throwing() {

			@Override
			public Throwable createException(String message) {
				return Throwing.this.createException(message);
			}

			@Override
			public String formatMessage(Object... args) {
				return messageFormatter.apply(args);
			}
		};
	}
}
