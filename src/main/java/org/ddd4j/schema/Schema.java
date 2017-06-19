package org.ddd4j.schema;

import java.io.IOException;

import org.ddd4j.Throwing;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.value.Value;

public interface Schema<T> extends Value<Schema<T>> {

	@FunctionalInterface
	interface Reader<T> {

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

	<X> Reader<X> createReader(Class<X> readerType);

	Writer<T> createWriter();

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
