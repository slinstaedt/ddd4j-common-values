package org.ddd4j.aggregate;

import java.util.function.Function;

import org.ddd4j.aggregate.Recorded.Committed;
import org.ddd4j.aggregate.Recorded.Uncommitted;
import org.ddd4j.contract.Require;
import org.ddd4j.value.Either;

public abstract class Recorded<E> implements Either<Uncommitted<E>, Committed<E>> {

	public static class Committed<E> extends Recorded<E> {

		private final Version version;

		private Committed(Identifier eventSourceId, E payload, Version version) {
			super(eventSourceId, payload);
			this.version = Require.nonNull(version);
		}

		public Version getVersion() {
			return version;
		}

		@Override
		public <X> X fold(Function<? super Uncommitted<E>, ? extends X> left, Function<? super Committed<E>, ? extends X> right) {
			return right.apply(this);
		}
	}

	public static class Uncommitted<E> extends Recorded<E> {

		private final Version expected;

		private Uncommitted(Identifier eventSourceId, E payload, Version expected) {
			super(eventSourceId, payload);
			this.expected = Require.nonNull(expected);
		}

		public Recorded.Committed<E> comitted(Version committedVersion) {
			return new Recorded.Committed<>(getEventSourceId(), getPayload(), committedVersion);
		}

		public Version getExpected() {
			return expected;
		}

		@Override
		public <X> X fold(Function<? super Uncommitted<E>, ? extends X> left, Function<? super Committed<E>, ? extends X> right) {
			return left.apply(this);
		}
	}

	public static <E> Uncommitted<E> uncommitted(Identifier eventSourceId, Version expected, E payload) {
		return new Uncommitted<>(eventSourceId, payload, expected);
	}

	private final Identifier eventSourceId;
	private final E payload;

	protected Recorded(Identifier eventSourceId, E payload) {
		this.eventSourceId = Require.nonNull(eventSourceId);
		this.payload = Require.nonNull(payload);
	}

	public Identifier getEventSourceId() {
		return eventSourceId;
	}

	public E getPayload() {
		return payload;
	}
}