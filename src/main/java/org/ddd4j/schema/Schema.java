package org.ddd4j.schema;

import org.ddd4j.io.ByteDataInput;
import org.ddd4j.io.ByteDataOutput;
import org.ddd4j.value.Value;

public interface Schema<T> extends Value<Schema<T>> {

	interface Fingerprint extends Value<Fingerprint> {
	}

	@FunctionalInterface
	interface Reader<T> {

		T readNext();
	}

	@FunctionalInterface
	interface Writer<T> {

		void writeNext(T value);
	}

	int hashCode(Object object);

	String getName();

	Fingerprint getFingerprint();

	// boolean contains(Type<?> type);

	Reader<T> createReader(ByteDataInput input);

	Writer<T> createWriter(ByteDataOutput output);
}
