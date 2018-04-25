package org.ddd4j.aggregate.domain;

import java.util.Optional;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.channel.SchemaCodec.Decoder.Extender.Wrapper;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Sequence;
import org.ddd4j.value.Value;

public interface Reaction<E> extends Value<Reaction<E>> {

	class Accepted<E> implements Reaction<E> {

		private static final byte EMPTY = 0;
		private static final byte SINGLE = 1;

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
		public Optional<E> serialize(WriteBuffer buffer) {
			if (events.isEmpty()) {
				buffer.put(EMPTY);
				return Optional.empty();
			} else {
				Require.that(events.size() == 1);
				buffer.put(SINGLE);
				return Optional.of(events.iterator().next());
			}
		}
	}

	class Rejected<E> extends Value.StringBased<Reaction<E>> implements Reaction<E> {

		private static final byte TYPE = 2;

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
		public Optional<E> serialize(WriteBuffer buffer) {
			buffer.put(TYPE).putUTF(value());
			return Optional.empty();
		}
	}

	@SafeVarargs
	static <E> Reaction<E> accepted(E... events) {
		return new Accepted<>(events);
	}

	static <E> Wrapper<E, Reaction<E>> deserialize(ReadBuffer buffer) {
		switch (buffer.get()) {
		case Accepted.SINGLE:
			return Wrapper.valuePresent(Accepted::new);
		case Accepted.EMPTY:
			return Wrapper.valueEmpty(new Accepted<>());
		case Rejected.TYPE:
			return Wrapper.valueDeserialized(buf -> new Rejected<>(buf.getUTF()));
		default:
			throw new IllegalArgumentException("Unknown Reaction type");
		}
	}

	static <T> Reaction<T> rejected(String reason) {
		return new Rejected<>(reason);
	}

	default Sequence<Reaction<E>> flatten() {
		return Sequence.of(this);
	}

	<X> X fold(Function<? super Accepted<E>, ? extends X> left, Function<? super Rejected<E>, ? extends X> right);

	@Override
	default Optional<E> serialize(WriteBuffer buffer) {
		serialize(buffer);
		return Optional.empty();
	}
}
