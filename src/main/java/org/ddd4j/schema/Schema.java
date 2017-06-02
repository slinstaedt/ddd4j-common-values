package org.ddd4j.schema;

import java.io.IOException;

import org.ddd4j.Throwing;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.value.Value;

public interface Schema<T> extends Value<Schema<T>> {

	@FunctionalInterface
	interface Reader<T> {

		default T read() {
			try {
				return readChecked();
			} catch (IOException e) {
				return Throwing.unchecked(e);
			}
		}

		T readChecked() throws IOException;
	}

	@FunctionalInterface
	interface Writer<T> {

		default void write(T value) {
			try {
				writeChecked(value);
			} catch (IOException e) {
				Throwing.unchecked(e);
			}
		}

		void writeChecked(T value) throws IOException;
	}

	boolean compatibleWith(Schema<?> existing);

	<X> Reader<X> createReader(ReadBuffer buffer, Class<X> targetType);

	Writer<T> createWriter(WriteBuffer buffer);

	boolean equal(Object o1, Object o2);

	Fingerprint getFingerprint();

	String getName();

	// boolean contains(Type<?> type);

	int hashCode(Object object);

	default void serializeFingerprintAndSchema(WriteBuffer buffer) {
		getFingerprint().serialize(buffer);
		this.serialize(buffer);
	}
}
