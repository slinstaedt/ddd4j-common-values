package org.ddd4j.infrastructure.channel.spi;

import java.util.function.Function;
import java.util.stream.Stream;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.util.Sequence;

public interface FlowControlled<T> {

	enum Mode {
		PAUSE, RESUME;
	}

	Promise<?> controlFlow(Mode mode, Sequence<T> values);

	default <X> FlowControlled<X> mapped(Function<? super X, Stream<? extends T>> mapper) {
		Require.nonNull(mapper);
		return (mode, values) -> controlFlow(mode, values.flatMap(mapper));
	}

	default Promise<?> pause() {
		return controlFlow(Mode.PAUSE, Sequence.empty());
	}

	default Promise<?> resume() {
		return controlFlow(Mode.RESUME, Sequence.empty());
	}
}
