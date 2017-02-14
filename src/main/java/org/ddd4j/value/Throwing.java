package org.ddd4j.value;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.ddd4j.contract.Require;

//TODO remove "T" prefix from all inner types?
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
	}

	@FunctionalInterface
	interface Task extends Runnable {

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
	interface TBiFunction<T, U, R> extends BiFunction<T, U, R> {

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
	interface TConsumer<T> extends Consumer<T> {

		@Override
		default void accept(T t) {
			try {
				acceptChecked(t);
			} catch (Exception e) {
				Throwing.unchecked(e);
			}
		}

		void acceptChecked(T t) throws Exception;

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

		default TPredicate<T> returningFalseOn(Class<? extends Exception> exceptionType) {
			return t -> {
				try {
					return testChecked(t);
				} catch (Exception e) {
					return exceptionType.isInstance(e) ? false : Throwing.unchecked(e);
				}
			};
		}

		default TConsumer<T> throwOnFail(Function<String, Exception> exceptionFactory) {
			return t -> {
				if (!testChecked(t)) {
					throw exceptionFactory.apply("Failed test " + this + " on: " + t);
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
	}

	String EXCEPTION_MESSAGE_TEMPLATE = "Could not invoke this with arguments %s";

	static Task action(Task action) {
		return Require.nonNull(action);
	}

	@SuppressWarnings("unchecked")
	static <X, E extends Exception> X any(Throwable throwable) throws E {
		Require.nonNull(throwable);
		throw (E) throwable;
	}

	static <T, U, R> TBiFunction<T, U, R> applied(TBiFunction<T, U, R> function) {
		return Require.nonNull(function);
	}

	static <T, R> TFunction<T, R> applied(TFunction<T, R> function) {
		return Require.nonNull(function);
	}

	static <T> TConsumer<T> consumed(TConsumer<T> consumer) {
		return Require.nonNull(consumer);
	}

	static Throwing of(Function<? super String, ? extends Throwable> exceptionFactory) {
		return Require.nonNull(exceptionFactory)::apply;
	}

	static <E extends Exception, R> TFunction<E, R> rethrow() {
		return e -> {
			throw e;
		};
	}

	static <T> Producer<T> task(Producer<T> supplier) {
		return Require.nonNull(supplier);
	}

	static <T> TPredicate<T> tested(TPredicate<T> predicate) {
		return Require.nonNull(predicate);
	}

	static <X> X unchecked(Throwable exception) {
		return Throwing.<X, RuntimeException> any(exception);
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

	default <T> Producer<T> asSupplier() {
		return () -> throwChecked();
	}

	Throwable createException(String message);

	default String formatMessage(Object... args) {
		return Arrays.asList(args).toString();
	}

	default <X> X throwChecked(Object... args) throws Exception {
		return any(createException(formatMessage(args)));
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
