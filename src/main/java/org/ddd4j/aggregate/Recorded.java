package org.ddd4j.aggregate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.aggregate.Recorded.Committed;
import org.ddd4j.aggregate.Recorded.Uncommitted;
import org.ddd4j.contract.Require;
import org.ddd4j.value.Either;
import org.ddd4j.value.collection.Seq;

public interface Recorded<E> extends Either<Uncommitted<E>, Committed<E>> {

	@FunctionalInterface
	interface EventProcessor<E, T> {

		T applyEvent(Committed<E> event);
	}

	interface CommitResult<E> {

		<X> X map(Function<Committed<E>, X> committed, Function<Conflict<E>, X> conflict);

		default CommitResult<E> visitCommitted(Consumer<Committed<E>> committed) {
			return map(c -> {
				committed.accept(c);
				return this;
			}, c -> {
				return this;
			});
		}

		default CommitResult<E> visitCommitted(Runnable committed) {
			return visitCommitted(c -> {
				committed.run();
			});
		}
	}

	class Conflict<E> implements CommitResult<E> {

		Identifier identifier;
		Version expected;
		Version actual;

		@Override
		public <X> X map(Function<Committed<E>, X> committed, Function<Conflict<E>, X> conflict) {
			return conflict.apply(this);
		}
	}

	class Committed<E> implements Recorded<E> {

		private final Identifier eventSource;
		private final E event;
		private final Version version;
		private final LocalDateTime timestamp;

		private Committed(Identifier eventSource, E payload, Version version) {
			this.eventSource = Require.nonNull(eventSource);
			this.event = Require.nonNull(payload);
			this.version = Require.nonNull(version);
			this.timestamp = LocalDateTime.now();
		}

		public E getEvent() {
			return event;
		}

		public LocalDateTime getTimestamp() {
			return timestamp;
		}

		public <X> Optional<Committed<X>> asOf(Class<X> type) {
			if (type.isInstance(event)) {
				return Optional.of(new Committed<>(eventSource, type.cast(event), version));
			} else {
				return Optional.empty();
			}
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

		private final Identifier eventSource;
		private final Seq<E> changes;

		private Uncommitted(Identifier eventSourceId, Seq<E> changes) {
			this.eventSource = Require.nonNull(eventSourceId);
			this.changes = Require.nonNull(changes);
		}

		public Seq<Committed<E>> comitted(Function<? super E, Version> committedVersion) {
			return changes.map().to(e -> new Committed<>(eventSource, e, committedVersion.apply(e))).compact();
		}

		@Override
		public <X> X fold(Function<? super Uncommitted<E>, ? extends X> left, Function<? super Committed<E>, ? extends X> right) {
			return left.apply(this);
		}
	}

	static <E> Uncommitted<E> uncommitted(Identifier eventSourceId, Seq<E> changes) {
		return new Uncommitted<>(eventSourceId, changes);
	}

	default Identifier getEventSource() {
		return fold(t -> t.eventSource, t -> t.eventSource);
	}

	default Seq<E> getPayload() {
		return fold(t -> t.changes, t -> Seq.of(t.event));
	}

	default <X> Optional<Seq<X>> matchedPayload(Class<X> type) {
		Seq<E> payload = getPayload();
		return payload.fold().allMatch(type::isInstance) ? Optional.of(payload.map().cast(type).target()) : Optional.empty();
	}
}