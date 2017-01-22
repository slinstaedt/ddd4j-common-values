package org.ddd4j.schema;

import java.io.Flushable;
import java.io.IOException;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.value.Value;

public interface Schema<T> extends Value<Schema<T>> {

	@FunctionalInterface
	interface Reader<T> {

		T read() throws IOException;
	}

	@FunctionalInterface
	interface Writer<T> extends Flushable {

		interface IOSink<T> {

			void write(T value) throws IOException;
		}

		enum Mode {

			NONE {

				@Override
				public <T> void apply(T value, IOSink<? super T> sink, Flushable flusher) throws IOException {
				}
			},
			WRITE {

				@Override
				public <T> void apply(T value, IOSink<? super T> sink, Flushable flusher) throws IOException {
					sink.write(value);
				}
			},
			FLUSH {

				@Override
				public <T> void apply(T value, IOSink<? super T> sink, Flushable flusher) throws IOException {
					flusher.flush();
				}
			},
			BOTH {

				@Override
				public <T> void apply(T value, IOSink<? super T> sink, Flushable flusher) throws IOException {
					sink.write(value);
					flusher.flush();
				}
			};

			public abstract <T> void apply(T value, IOSink<? super T> sink, Flushable flusher) throws IOException;
		}

		void apply(Mode mode, T value) throws IOException;

		@Override
		default void flush() throws IOException {
			apply(Mode.FLUSH, null);
		}

		default void write(T value) throws IOException {
			apply(Mode.WRITE, value);
		}

		default void writeAndFlush(T value) throws IOException {
			apply(Mode.BOTH, value);
		}
	}

	boolean compatibleWith(Schema<?> existing);

	Reader<T> createReader(ReadBuffer buffer);

	Writer<T> createWriter(WriteBuffer buffer);

	boolean equal(Object o1, Object o2);

	Fingerprint getFingerprint();

	// boolean contains(Type<?> type);

	String getName();

	int hashCode(Object object);
}
