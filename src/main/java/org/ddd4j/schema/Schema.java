package org.ddd4j.schema;

import java.io.Flushable;
import java.io.IOException;

import org.ddd4j.io.Input;
import org.ddd4j.io.Output;
import org.ddd4j.value.Value;

public interface Schema<T> extends Value<Schema<T>> {

	@FunctionalInterface
	interface Reader<T> {

		T read() throws IOException;
	}

	@FunctionalInterface
	interface Writer<T> extends Flushable {

		void writeOrFlush(T value, boolean flush) throws IOException;

		default void write(T value) throws IOException {
			writeOrFlush(value, false);
		}

		default void writeAndFlush(T value) throws IOException {
			write(value);
			flush();
		}

		@Override
		default void flush() throws IOException {
			writeOrFlush(null, true);
		}
	}

	boolean compatibleWith(Schema<?> existing);

	int hashCode(Object object);

	boolean equal(Object o1, Object o2);

	String getName();

	Fingerprint getFingerprint();

	// boolean contains(Type<?> type);

	Reader<T> createReader(Input input);

	Writer<T> createWriter(Output output);
}
