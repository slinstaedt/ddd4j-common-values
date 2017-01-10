package org.ddd4j.infrastructure.log;

import java.util.function.Consumer;

import org.ddd4j.value.Throwing.Closeable;
import org.ddd4j.value.versioned.Committed;

public interface Log<K, V> extends Closeable {

	void subscribe(Consumer<Committed<K, V[]>> consumer, RevisionsCallback callback);
}
