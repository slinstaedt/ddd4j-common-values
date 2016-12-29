package org.ddd4j.value.versioned;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.contract.Require;

public class Committed<E> implements Recorded<E>, CommitResult<E> {

	private final Identifier identifier;
	private final E entry;
	private final Revision revision;
	private final Revision nextExpected;
	private final LocalDateTime timestamp;

	public Committed(Identifier identifier, E entry, Revision revision, Revision nextExpected, LocalDateTime timestamp) {
		this.identifier = Require.nonNull(identifier);
		this.entry = Require.nonNull(entry);
		this.revision = Require.nonNull(revision);
		this.nextExpected = Require.nonNull(nextExpected);
		this.timestamp = Require.nonNull(timestamp);
	}

	public <X> Optional<Committed<X>> asOf(Class<X> type) {
		if (type.isInstance(entry)) {
			return Optional.of(new Committed<>(identifier, type.cast(entry), revision, nextExpected, timestamp));
		} else {
			return Optional.empty();
		}
	}

	@Override
	public <X> X foldRecorded(Function<Uncommitted<E>, X> uncommitted, Function<Committed<E>, X> committed) {
		return committed.apply(this);
	}

	@Override
	public <X> X foldResult(Function<Committed<E>, X> committed, Function<Conflict<E>, X> conflict) {
		return committed.apply(this);
	}

	public E getEntry() {
		return entry;
	}

	@Override
	public Identifier getIdentifier() {
		return identifier;
	}

	public LocalDateTime getTimestamp() {
		return timestamp;
	}

	public Revision getVersion() {
		return revision;
	}
}