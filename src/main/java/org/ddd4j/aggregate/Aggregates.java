package org.ddd4j.aggregate;

import java.util.Optional;

import org.ddd4j.util.Require;
import org.ddd4j.value.Nothing;
import org.ddd4j.value.behavior.Identifier;
import org.ddd4j.value.versioned.Revisions;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.EventProcessor;

public interface Aggregates extends EventProcessor<Object, Nothing> {

	class Aggregate {

		private final Identifier identifier;
		private final Revisions version;
		private final Object state;

		public Aggregate(Committed<?> committed) {
			this(committed.getEventSourceId(), committed.getVersion(), null);
		}

		public Aggregate(Identifier identifier, Revisions version, Object state) {
			this.identifier = Require.nonNull(identifier);
			this.version = Require.nonNull(version);
			this.state = Require.nonNull(state);
		}

		public Identifier getIdentifier() {
			return identifier;
		}

		public Revisions getVersion() {
			return version;
		}

		public Object getState() {
			return state;
		}
	}

	Optional<Aggregate> get(Identifier identifier);

	Revisions put(Aggregate aggregate);

	@Override
	default Nothing applyEvent(Committed<Object> committed) {
		Aggregate current = get(committed.getEventSourceId()).orElse(new Aggregate(committed));
		Revisions expected = current.getVersion();
		Require.that(committed.getVersion().before(expected));
		if (committed.getVersion().equal(expected)) {
			// TODO
		}
		return Nothing.INSTANCE;
	}
}
