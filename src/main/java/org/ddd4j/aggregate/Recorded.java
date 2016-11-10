package org.ddd4j.aggregate;

import java.util.Optional;
import java.util.function.Function;

import org.ddd4j.aggregate.Recorded.Committed;
import org.ddd4j.aggregate.Recorded.Uncommitted;
import org.ddd4j.contract.Require;
import org.ddd4j.value.Either;

public interface Recorded<E> extends Either<Uncommitted<E>, Committed<E>> {

	@FunctionalInterface
	interface EventProcessor<E, T> {

		T applyEvent(Committed<E> event);
	}

	class Committed<E> implements Recorded<E> {

		private final Identifier eventSourceId;
		private final E payload;
		private final Version version;

		private Committed(Identifier eventSourceId, E payload, Version version) {
			this.eventSourceId = Require.nonNull(eventSourceId);
			this.payload = Require.nonNull(payload);
			this.version = Require.nonNull(version);
		}

		@Override
		public <X> X fold(Function<? super Uncommitted<E>, ? extends X> left, Function<? super Committed<E>, ? extends X> right) {
			return right.apply(this);
		}

		public Version getVersion() {
			return version;
		}
	}

	class Uncommitted<E> implements Recorded<E> {

		private final Identifier eventSourceId;
		private final E payload;

		private Uncommitted(Identifier eventSourceId, E payload) {
			this.eventSourceId = Require.nonNull(eventSourceId);
			this.payload = Require.nonNull(payload);
		}

		public Recorded.Committed<E> comitted(Version committedVersion) {
			return new Recorded.Committed<>(eventSourceId, payload, committedVersion);
		}

		@Override
		public <X> X fold(Function<? super Uncommitted<E>, ? extends X> left, Function<? super Committed<E>, ? extends X> right) {
			return left.apply(this);
		}
	}

	static <E> Uncommitted<E> uncommitted(Identifier eventSourceId, E payload) {
		return new Uncommitted<>(eventSourceId, payload);
	}

	default Identifier getEventSourceId() {
		return fold(t -> t.eventSourceId, t -> t.eventSourceId);
	}

	default E getPayload() {
		return fold(t -> t.payload, t -> t.payload);
	}

	default <X> Optional<X> matchedPayload(Class<X> type) {
		E payload = getPayload();
		return type.isInstance(payload) ? Optional.of(type.cast(payload)) : Optional.empty();
	}
}