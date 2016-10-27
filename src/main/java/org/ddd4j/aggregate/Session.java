package org.ddd4j.aggregate;

import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.behavior.Reaction;
import org.ddd4j.value.collection.Map;
import org.ddd4j.value.collection.Seq;

public class Session {

	// TODO needed?
	public static abstract class Recorded<E> {

		public static class Committed<E> extends Recorded<E> {

			private final Version version;

			private Committed(Identifier eventSourceId, E payload, Version version) {
				super(eventSourceId, payload);
				this.version = Require.nonNull(version);
			}

			public Version getVersion() {
				return version;
			}
		}

		public static class Uncommitted<E> extends Recorded<E> {

			private Uncommitted(Identifier eventSourceId, E payload, Version version) {
				super(eventSourceId, payload);
			}

			public Committed<E> comitted(Version version) {
				return new Committed<>(getEventSourceId(), getPayload(), version);
			}
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

	public static Session create() {
		// TODO Auto-generated method stub
		return new Session();
	}

	private Map<Identifier, Object> commands;
	private Map<Identifier, Object> events;

	public Session() {
	}

	public <E, T> Reaction<T> record(Function<? super E, ? extends T> handler, E event) {
		T result = handler.apply(event);
		return Reaction.accepted(result, Seq.singleton(event));
	}
}