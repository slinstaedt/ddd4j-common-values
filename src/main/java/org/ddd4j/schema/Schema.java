package org.ddd4j.schema;

import java.io.IOException;

import org.ddd4j.Throwing;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.util.Type;
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

	static Schema<?> deserializeFromFactory(Context context, ReadBuffer buffer) {
		return context.specific(SchemaFactory.KEY).withOrFail(buffer.getUTF()).readSchema(buffer);
	}

	boolean compatibleWith(Schema<?> existing);

	<X> Reader<X> createReader(Type<X> readerType);

	Writer<T> createWriter();

	boolean equal(Object o1, Object o2);

	Fingerprint getFingerprint();

	String getFactoryName();

	int hashCode(Object object);

	default <X> X read(Type<X> readerType, ReadBuffer buffer) {
		return createReader(readerType).read(buffer);
	}

	default void write(WriteBuffer buffer, T value) {
		createWriter().write(buffer, value);
	}

	default void serializeWithFactoryName(WriteBuffer buffer) {
		buffer.putUTF(getFactoryName());
		buffer.accept(this::serialize);
	}
}
