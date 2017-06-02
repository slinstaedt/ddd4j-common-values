package org.ddd4j.repository;

import java.util.function.BiFunction;

import org.ddd4j.repository.VersionedRepository.Versioned;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public interface VersionedRepository<K, S, C> extends Repository<K, Versioned<S, C>> {

	class Versioned<S, C> {

		private S state;
		private C[] changes;

		public <X> X apply(BiFunction<? super S, Seq<C>, X> replayer) {
			return replayer.apply(state, Seq.of(changes));
		}
	}

	Committed<K, Versioned<S, C>> get(K key, Revision revision);
}
