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

		default void write(T value) throws IOException {
			apply(Mode.WRITE, value);
		}

		default void writeAndFlush(T value) throws IOException {
			apply(Mode.BOTH, value);
		}

		@Override
		default void flush() throws IOException {
			apply(Mode.FLUSH, null);
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
