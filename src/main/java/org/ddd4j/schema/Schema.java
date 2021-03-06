package org.ddd4j.schema;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing;
import org.ddd4j.util.Type;
import org.ddd4j.util.value.Value;

public interface Schema<T> extends Value<Schema<T>> {

	@FunctionalInterface
	interface Reader<T> {

		default Supplier<T> asSupplier(ReadBuffer buffer) {
			Require.nonNull(buffer);
			return () -> read(buffer);
		}

		default T read(ReadBuffer buffer) {
			try {
				return readChecked(buffer);
			} catch (IOException e) {
				return Throwing.unchecked(e);
			}
		}

		T readChecked(ReadBuffer buffer) throws IOException;
	}

	@FunctionalInterface
	interface Writer<T> {

		default Consumer<T> asConsumer(WriteBuffer buffer) {
			Require.nonNull(buffer);
			return value -> write(buffer, value);
		}

		default void write(WriteBuffer buffer, T value) {
			try {
				writeChecked(buffer, value);
			} catch (IOException e) {
				Throwing.unchecked(e);
			}
		}

		void writeChecked(WriteBuffer buffer, T value) throws IOException;
	}

	boolean compatibleWith(Schema<?> existing);

	<X> Reader<X> createReader(Type<X> readerType);

	Writer<T> createWriter();

	boolean equal(Object o1, Object o2);

	Fingerprint getFingerprint();

	int hashCode(Object object);

	@Override
	void serialize(WriteBuffer buffer);
}
