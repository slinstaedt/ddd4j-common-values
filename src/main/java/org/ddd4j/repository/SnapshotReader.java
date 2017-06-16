package org.ddd4j.repository;

import java.util.function.BiFunction;

import org.ddd4j.repository.SnapshotReader.Projectable;
import org.ddd4j.repository.api.Reader;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public interface SnapshotReader<K, S, C> extends Reader<K, Projectable<S, C>> {

	class Projectable<S, C> {

		private S state;
		private C[] changes;

		public <X> X apply(BiFunction<? super S, Seq<C>, X> projector) {
			return projector.apply(state, Seq.of(changes));
		}
	}

	Committed<K, Projectable<S, C>> get(K key, Revision revision);
}
