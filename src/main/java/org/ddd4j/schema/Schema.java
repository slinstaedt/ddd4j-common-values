package org.ddd4j.schema;

import java.io.Flushable;

import org.ddd4j.io.ByteDataInput;
import org.ddd4j.io.ByteDataOutput;
import org.ddd4j.value.Opt;
import org.ddd4j.value.Value;

public interface Schema<T> extends Value<Schema<T>> {

	interface Fingerprint extends Value<Fingerprint> {
	}

	@FunctionalInterface
	interface Reader<T> {

		T read();
	}

	@FunctionalInterface
	interface Writer<T> extends Flushable {

		void writeOrFlush(Opt<T> value);

		default void write(T value) {
			writeOrFlush(Opt.of(value));
		}

		default void writeAndFlush(T value) {
			write(value);
			flush();
		}

		@Override
		default void flush() {
			writeOrFlush(Opt.none());
		}
	}

	boolean compatibleWith(Schema<?> existing);

	int hashCode(Object object);

	boolean equal(Object o1, Object o2);

	String getName();

	Fingerprint getFingerprint();

	// boolean contains(Type<?> type);

	Reader<T> createReader(ByteDataInput input);

	Writer<T> createWriter(ByteDataOutput output);
}
