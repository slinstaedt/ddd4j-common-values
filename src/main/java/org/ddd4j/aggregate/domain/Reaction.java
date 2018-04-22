package org.ddd4j.aggregate.domain;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.SchemaCodec.Decoder;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Sequence;
import org.ddd4j.value.Value;
import org.ddd4j.value.collection.Seq;

public interface Reaction<E> extends Value<Reaction<E>> {

	class Accepted<E> implements Reaction<E> {

		private static final byte TYPE = 0;

		private final Sequence<? extends E> events;

		@SafeVarargs
		private Accepted(E... events) {
			this.events = Sequence.of(Require.nonNulls(events));
		}

		@Override
		public Sequence<Reaction<E>> flatten() {
			return events.isEmpty() ? Sequence.of(this) : events.map(Accepted::new);
		}

		@Override
		public <X> X fold(Function<? super Accepted<E>, ? extends X> left, Function<? super Rejected<E>, ? extends X> right) {
			return left.apply(this);
		}

		public Sequence<? extends E> getEvents() {
			return events;
		}

		@Override
		public void serialize(WriteBuffer buffer) {
			buffer.put(TYPE);
		}
	}

	class Failed<E> extends Value.StringBased<Reaction<E>> implements Reaction<E> {

		private static final byte TYPE = 2;

		private Failed(String stackTrace) {
			super(stackTrace);
		}

		@Override
		public <X> X fold(Function<? super Accepted<E>, ? extends X> left, Function<? super Rejected<E>, ? extends X> right) {
			// TODO
			return null;
		}

		public String getStackTrace() {
			return value();
		}

		@Override
		public void serialize(WriteBuffer buffer) {
			buffer.put(TYPE).putUTF(value());
		}
	}

	class Rejected<E> extends Value.StringBased<Reaction<E>> implements Reaction<E> {

		private static final byte TYPE = 1;

		private Rejected(String reason) {
			super(reason);
		}

		@Override
		public <X> X fold(Function<? super Accepted<E>, ? extends X> left, Function<? super Rejected<E>, ? extends X> right) {
			return right.apply(this);
		}

		public String getReason() {
			return value();
		}

		@Override
		public void serialize(WriteBuffer buffer) {
			buffer.put(TYPE).putUTF(value());
		}
	}

	@SafeVarargs
	static <E> Reaction<E> accepted(E... events) {
		return new Accepted<>(events);
	}

	static <E> Decoder.Extender.Constructor<E, Reaction<E>> deserialize(ReadBuffer buffer) {
		switch (buffer.get()) {
		case Accepted.TYPE:
			return (val, buf) -> val.get().thenApply(Accepted::new);
		case Rejected.TYPE:
			return (val, buf) -> Promise.completed(null);
		}
	}

	static <E> Reaction<E> failed(Exception exception) {
		StringWriter writer = new StringWriter();
		exception.printStackTrace(new PrintWriter(writer, true));
		return new Failed<>(writer.toString());
	}

	static <T> Reaction<T> rejected(String reason) {
		return new Rejected<>(reason);
	}

	default Sequence<Reaction<E>> flatten() {
		return Sequence.of(this);
	}

	<X> X fold(Function<? super Accepted<E>, ? extends X> left, Function<? super Rejected<E>, ? extends X> right);

	default <X> X foldReaction(Function<Seq<? extends E>, ? extends X> accepted, BiFunction<String, Object[], ? extends X> rejected) {
		return fold(a -> accepted.apply(a.getEvents()), r -> rejected.apply(r.getMessage(), r.getArguments()));
	}

	// TODO refactor to 'serialize' returning Object
	default Optional<E> write(WriteBuffer buffer) {
		serialize(buffer);
		return Optional.empty();
	}
}
