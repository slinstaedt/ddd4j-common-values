package org.ddd4j.value.versioned;

import java.util.Optional;
import java.util.function.Function;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.value.collection.Seq;

public interface Recorded<E> {

	static <E> Uncommitted<E> uncommitted(Identifier source, Seq<E> changes, Revision expected) {
		return new Uncommitted<>(source, changes, expected);
	}

	<X> X foldRecorded(Function<Uncommitted<E>, X> uncommitted, Function<Committed<E>, X> committed);

	Identifier getIdentifier();

	default <X> Optional<Seq<X>> matchedPayload(Class<X> type) {
		Seq<E> payload = getEntry();
		return payload.fold().allMatch(type::isInstance) ? Optional.of(payload.map().cast(type).target()) : Optional.empty();
	}
}