package org.ddd4j.infrastructure.scheduler;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import java.util.List;
import java.util.function.IntFunction;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing.TCloseable;
import org.ddd4j.value.Throwing.TRunnable;
import org.ddd4j.value.Throwing.TSupplier;

@FunctionalInterface
public interface Resource<T> extends TCloseable {

	class Lazy<T> implements Resource<T> {

		private TSupplier<? extends Resource<T>> delegate;

		public Lazy(TSupplier<? extends Resource<T>> factory) {
			Require.nonNull(factory);
			delegate = () -> {
				Resource<T> resource = Require.nonNull(factory.get());
				delegate = () -> resource;
				return resource;
			};
		}

		@Override
		public void closeChecked() throws Exception {
			delegate.getChecked().closeChecked();
		}

		@Override
		public List<? extends T> request(int n) throws Exception {
			return delegate.getChecked().request(n);
		}
	}

	class Reading<T> implements Resource<T> {

		private final IntFunction<? extends List<? extends T>> reader;
		private final AutoCloseable closer;

		public Reading(IntFunction<? extends List<? extends T>> reader, AutoCloseable closer) {
			this.reader = Require.nonNull(reader);
			this.closer = Require.nonNull(closer);
		}

		@Override
		public void closeChecked() throws Exception {
			closer.close();
		}

		@Override
		public List<? extends T> request(int n) throws Exception {
			return reader.apply(n);
		}
	}

	static <T> Resource<T> lazy(TSupplier<? extends Resource<T>> factory) {
		return new Lazy<>(factory);
	}

	static <T> Resource<T> of(IntFunction<? extends List<? extends T>> reader, AutoCloseable closer) {
		return new Reading<>(reader, closer);
	}

	static <T> Resource<T> of(TSupplier<? extends T> reader, TRunnable closer) {
		Require.nonNullElements(reader, closer);
		return of((int n) -> reader.<List<T>> map(t -> t != null ? singletonList(t) : emptyList()).get(), closer::runChecked);
	}

	@Override
	default void closeChecked() throws Exception {
	}

	List<? extends T> request(int n) throws Exception;
}
