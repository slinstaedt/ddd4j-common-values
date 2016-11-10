package org.ddd4j.aggregate;

import java.util.Optional;

import org.ddd4j.aggregate.Recorded.Committed;
import org.ddd4j.aggregate.Recorded.EventProcessor;
import org.ddd4j.contract.Require;
import org.ddd4j.value.Nothing;

public interface Aggregates extends EventProcessor<Object, Nothing> {

	class Aggregate {

		private final Identifier identifier;
		private final Version version;
		private final Object state;

		public Aggregate(Committed<?> committed) {
			this(committed.getEventSourceId(), committed.getVersion(), null);
		}

		public Aggregate(Identifier identifier, Version version, Object state) {
			this.identifier = Require.nonNull(identifier);
			this.version = Require.nonNull(version);
			this.state = Require.nonNull(state);
		}

		public Identifier getIdentifier() {
			return identifier;
		}

		public Version getVersion() {
			return version;
		}

		public Object getState() {
			return state;
		}
	}

	Optional<Aggregate> get(Identifier identifier);

	Version put(Aggregate aggregate);

	@Override
	default Nothing applyEvent(Committed<Object> committed) {
		Aggregate current = get(committed.getEventSourceId()).orElse(new Aggregate(committed));
		Version expected = current.getVersion();
		Require.that(committed.getVersion().before(expected));
		if (committed.getVersion().equal(expected)) {
			// TODO
		}
		return Nothing.INSTANCE;
	}
}
