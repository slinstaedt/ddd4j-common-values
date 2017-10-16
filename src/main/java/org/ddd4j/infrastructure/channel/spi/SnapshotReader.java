package org.ddd4j.infrastructure.channel.spi;

import java.util.Optional;
import java.util.function.BiFunction;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.spi.SnapshotReader.Projectable;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelRevision;
import org.ddd4j.infrastructure.domain.value.CommittedRecords;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Sequence;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public interface SnapshotReader<K, S, C> extends Reader<K, Projectable<S, C>> {

	class ColdReaderBased implements SnapshotReader<ReadBuffer, ReadBuffer, ReadBuffer> {

		private ChannelName name;
		private ColdReader state;
		private ColdReader changes;

		@Override
		public Promise<Committed<ReadBuffer, Projectable<ReadBuffer, ReadBuffer>>> get(ReadBuffer key, Revision revision) {
			// TODO Auto-generated method stub
			Promise<CommittedRecords> promise = state.get(new ChannelRevision(name, revision));
			promise.thenApply(cr -> cr.commits(name, key).last()).thenCompose(o -> fetchChanges(key, revision, o));
			return null;
		}

		private Promise<Sequence<Committed<ReadBuffer, ReadBuffer>>> fetchChanges(ReadBuffer key, Revision revision,
				Optional<Committed<ReadBuffer, ReadBuffer>> lastState) {
			Revision rev = lastState.map(Committed::getActual).orElse(revision);
			return changes.get(new ChannelRevision(name, rev)).thenApply(cr -> cr.commits(name, key));
		}
	}

	class Projectable<S, C> {

		private S state;
		private C[] changes;

		public Sequence<C> getChanges() {
			return Sequence.of(changes);
		}

		public Optional<S> getState() {
			return Optional.ofNullable(state);
		}

		public <X> X map(BiFunction<? super S, Sequence<C>, X> projector) {
			return projector.apply(state, Sequence.of(changes));
		}
	}

	Promise<Committed<K, Projectable<S, C>>> get(K key, Revision revision);

	@Override
	default Promise<Optional<Committed<K, Projectable<S, C>>>> get(K key) {
		// TODO Auto-generated method stub
		return null;
	}
}
