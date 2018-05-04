package org.ddd4j.value.collection;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import org.ddd4j.util.Require;
import org.ddd4j.value.Opt;
import org.ddd4j.value.collection.Ref.RefOpt;

@FunctionalInterface
public interface Iter<T> {

	@FunctionalInterface
	interface Able<T> extends Iterable<T> {

		static <T> Iter.Able<T> wrap(Iterable<T> iterable) {
			Require.nonNull(iterable);
			return new Able<T>() {

				@Override
				public Iter<T> iter() {
					return Iter.wrap(iterable.iterator());
				}

				@Override
				public Iterator<T> iterator() {
					return iterable.iterator();
				}

				@Override
				public Spliterator<T> spliterator() {
					return iterable.spliterator();
				}
			};
		}

		default Seq<T> asSequence() {
			return new Seq.Value<>(() -> StreamSupport.stream(spliterator(), false));
		}

		Iter<T> iter();

		@Override
		default Iterator<T> iterator() {
			return Iter.wrap(iter());
		}
	}

	@FunctionalInterface
	interface ConditionalConsumer<T> extends Consumer<T> {

		default boolean acceptIf(BooleanSupplier hasNext, Supplier<? extends T> nextElement) {
			return hasNext.getAsBoolean() ? acceptReturning(nextElement.get(), true) : false;
		}

		default boolean acceptReturning(T element, boolean result) {
			accept(element);
			return result;
		}
	}

	static <T> Iterator<T> wrap(Iter<T> delegate) {
		return new Iterator<T>() {

			private final RefOpt<T> ref = RefOpt.create(delegate.next());

			@Override
			public boolean hasNext() {
				return !ref.isEmpty();
			}

			@Override
			public T next() {
				T next = ref.getNullable();
				delegate.visitNextOrElse(ref.asNonEmpty()::set, ref::empty);
				return next;
			}
		};
	}

	static <T> Iter<T> wrap(Iterator<T> delegate) {
		Require.nonNull(delegate);
		return () -> delegate.hasNext() ? Opt.of(delegate.next()) : Opt.none();
	}

	default Iterator<T> asIterator() {
		return wrap(this);
	}

	default void forEachRemaining(Consumer<? super T> consumer) {
		while (visitNext(consumer::accept)) {
		}
	}

	Opt<T> next();

	/**
	 * Visits the next element of this iterator by accepting it's element, if available.
	 *
	 * @param consumer
	 *            The element visitor
	 * @return true, if the next element was consumed, false otherwise
	 */
	default boolean visitNext(ConditionalConsumer<? super T> consumer) {
		return !next().visitNullable(consumer).isEmpty();
	}

	default boolean visitNextOrElse(ConditionalConsumer<? super T> consumer, Runnable other) {
		boolean visited = visitNext(consumer);
		if (!visited) {
			other.run();
		}
		return visited;
	}

	default boolean visitNextOrOptional(ConditionalConsumer<? super T> consumer, Opt<? extends T> fallback) {
		return visitNextOrElse(consumer, () -> fallback.ifNullablePresent(consumer));
	}

	default boolean visitNextOrSupplied(ConditionalConsumer<? super T> consumer, Supplier<? extends T> fallback) {
		return visitNextOrElse(consumer, () -> consumer.accept(fallback.get()));
	}
}
