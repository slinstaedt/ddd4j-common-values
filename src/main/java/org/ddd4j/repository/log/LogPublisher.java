package org.ddd4j.repository.log;

import java.util.Set;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.SchemaCodec;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RepartitioningListener;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelSpec;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.repository.ReactiveListener;
import org.ddd4j.value.versioned.Committed;

public interface LogPublisher {

	interface Listener<K, V> extends SourceListener<K, V>, CompletionListener, ErrorListener, RepartitioningListener {
	}

	default boolean isSubcribed() {
		return !subscribed().isEmpty();
	}

	default void subscribe(ChannelName name, Listener<ReadBuffer, ReadBuffer> listener) {
		subscribe(name, listener, listener, listener, listener, listener);
	}

	Promise<Integer> subscribe(ChannelName name, SourceListener<?, ?> handle, SourceListener<ReadBuffer, ReadBuffer> source,
			CompletionListener completion, ErrorListener error, RepartitioningListener repartitioning);

	default Promise<Integer> subscribe(ChannelName name, SourceListener<ReadBuffer, ReadBuffer> source, CompletionListener completion,
			ErrorListener error, RepartitioningListener repartitioning) {
		return subscribe(name, source, source, completion, error, repartitioning);
	}

	default <K, V> void subscribe(SchemaCodec.Factory factory, ChannelSpec<K, V> spec, SourceListener<K, V> source,
			CompletionListener completion, ErrorListener error, RepartitioningListener repartitioning) {
		SchemaCodec.Decoder<V> decoder = factory.decoder(spec);
		SourceListener<ReadBuffer, ReadBuffer> mappedListener = source.mapPromised(spec::deserializeKey, decoder::decode, error);
		subscribe(spec.getName(), source, mappedListener, completion, error, repartitioning);
	}

	Set<ChannelName> subscribed();

	default Flow.Publisher<Committed<ReadBuffer, ReadBuffer>> subscriber(ChannelName name) {
		Require.nonNull(name);
		return s -> subscribe(name, new ReactiveListener<>(s, unsubscriber(name)));
	}

	void unsubscribe(ChannelName name, SourceListener<?, ?> listener);

	default Consumer<? super SourceListener<?, ?>> unsubscriber(ChannelName name) {
		Require.nonNull(name);
		return l -> unsubscribe(name, l);
	}
}
