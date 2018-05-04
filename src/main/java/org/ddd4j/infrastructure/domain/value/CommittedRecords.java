package org.ddd4j.infrastructure.domain.value;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.util.value.Sequence;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public class CommittedRecords {

	public static final CommittedRecords EMPTY = new CommittedRecords(Collections.emptyMap());

	public static CommittedRecords copied(Map<ChannelName, List<Committed<ReadBuffer, ReadBuffer>>> values) {
		Map<ChannelName, Sequence<Committed<ReadBuffer, ReadBuffer>>> copy = new HashMap<>();
		values.forEach((r, c) -> copy.put(r, Sequence.ofCopied(c)));
		return new CommittedRecords(copy);
	}

	private final Map<ChannelName, Sequence<Committed<ReadBuffer, ReadBuffer>>> values;

	private CommittedRecords(Map<ChannelName, Sequence<Committed<ReadBuffer, ReadBuffer>>> values) {
		this.values = Require.nonNull(values);
	}

	public Committed<ReadBuffer, ReadBuffer> commit(ChannelName name, Revision actual) {
		return commits(name).filter(c -> c.getActual().equals(actual)).head().orElseThrow(NoSuchElementException::new);
	}

	public Committed<ReadBuffer, ReadBuffer> commit(ChannelRevision spec) {
		return commit(spec.getName(), spec.getRevision());
	}

	public Sequence<Committed<ReadBuffer, ReadBuffer>> commits(ChannelName name) {
		return values.getOrDefault(name, Sequence.empty());
	}

	public Sequence<Committed<ReadBuffer, ReadBuffer>> commits(ChannelName name, ReadBuffer key) {
		return commits(name).filter(c -> c.getKey().equals(key)); // TODO
	}

	public void forEach(BiConsumer<ChannelName, Committed<ReadBuffer, ReadBuffer>> consumer) {
		values.forEach((n, s) -> s.forEach(c -> consumer.accept(n, c)));
	}

	public void forEachOrEmpty(BiConsumer<ChannelName, Committed<ReadBuffer, ReadBuffer>> consumer, Runnable empty) {
		if (values.isEmpty()) {
			empty.run();
		} else {
			forEach(consumer);
		}
	}

	public boolean isEmpty() {
		return values.isEmpty();
	}

	public boolean isNotEmpty() {
		return !isEmpty();
	}

	public int size() {
		return values.values().stream().mapToInt(Sequence::size).sum();
	}
}