package org.ddd4j.aggregate.domain;

import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.io.codec.Decoding;
import org.ddd4j.util.Require;
import org.ddd4j.util.value.Sequence;
import org.ddd4j.util.value.Value;

public interface Reaction<E> extends Value<Reaction<E>> {

	class Accepted<E> implements Reaction<E> {

		private static final byte TYPE = 0;

		private final Sequence<E> events;

		private Accepted(Sequence<E> events) {
			this.events = Require.nonNull(events);
		}

		@Override
		public <X> X fold(Function<? super Accepted<E>, ? extends X> left, Function<? super Rejected<E>, ? extends X> right) {
			return left.apply(this);
		}

		public Sequence<? extends E> getEvents() {
			return events;
		}

		@Override
		public void serialize(WriteBuffer buffer, Consumer<Sequence<E>> writer) {
			buffer.put(TYPE);
			writer.accept(events);
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
		public void serialize(WriteBuffer buffer, Consumer<Sequence<E>> writer) {
			buffer.put(TYPE).putUTF(getReason());
		}
	}

	@SafeVarargs
	static <E> Reaction<E> accepted(E... events) {
		return new Accepted<>(Sequence.of(events));
	}

	static <E> Decoding<Sequence<E>, Reaction<E>> deserialize(ReadBuffer buffer) {
		switch (buffer.get()) {
		case Accepted.TYPE:
			return Decoding.valueRead(Accepted::new);
		case Rejected.TYPE:
			return Decoding.valueDeserialized(buf -> new Rejected<>(buf.getUTF()));
		default:
			throw new IllegalArgumentException("Unknown Reaction type");
		}
	}

	static <T> Reaction<T> rejected(String reason) {
		return new Rejected<>(reason);
	}

	<X> X fold(Function<? super Accepted<E>, ? extends X> left, Function<? super Rejected<E>, ? extends X> right);

	void serialize(WriteBuffer buffer, Consumer<Sequence<E>> writer);
}
