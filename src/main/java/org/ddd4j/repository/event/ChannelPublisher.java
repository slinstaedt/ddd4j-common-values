package org.ddd4j.repository.event;

import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.ddd4j.Require;
import org.ddd4j.Throwing.Closeable;
import org.ddd4j.infrastructure.channel.SchemaCodec;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelSpec;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.repository.FlowSubscription;
import org.ddd4j.value.versioned.Committed;

public class ChannelPublisher<C> implements Closeable {

	public interface ListenerFactory<C> {

		ChannelListener create(CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, C callback);

		default ListenerFactory<C> wrapListener(UnaryOperator<ChannelListener> wrapper) {
			Require.nonNull(wrapper);
			return (commit, error, callback) -> wrapper.apply(create(commit, error, callback));
		}
	}

	public interface Publisher<K, V, C> {

		default Flow.Publisher<Committed<K, V>> forCallback(C callback) {
			Require.nonNull(callback);
			return s -> subscribe(s, callback);
		}

		default Flow.Publisher<Committed<K, V>> forCallback(Function<? super Subscriber<? super Committed<K, V>>, ? extends C> callback) {
			Require.nonNull(callback);
			return s -> subscribe(s, callback.apply(s));
		}

		void subscribe(Subscriber<? super Committed<K, V>> subscriber, C callback);
	}

	private final SubscribedChannels channels;
	private final SchemaCodec.Factory codecFactory;
	private final ListenerFactory<C> listenerFactory;

	public ChannelPublisher(SubscribedChannels channels, SchemaCodec.Factory codecFactory, ListenerFactory<C> listenerFactory) {
		this.channels = Require.nonNull(channels);
		this.codecFactory = Require.nonNull(codecFactory);
		this.listenerFactory = Require.nonNull(listenerFactory);
	}

	@Override
	public void closeChecked() throws Exception {
		channels.closeChecked();
	}

	public Set<ChannelName> getSubscribedChannels() {
		return channels.getNames();
	}

	public boolean isSubcribed() {
		return !getSubscribedChannels().isEmpty();
	}

	public Publisher<ReadBuffer, ReadBuffer, C> publisher(ChannelName name) {
		Require.nonNull(name);
		return (s, c) -> channels.subscribe(name, s, FlowSubscription.createListener(listenerFactory, c, s, unsubscriber(name)));
	}

	public <K, V> Publisher<K, V, C> publisher(ChannelSpec<K, V> spec) {
		return (s, c) -> channels.subscribe(spec.getName(), s,
				FlowSubscription.createListener(listenerFactory, c, s, unsubscriber(spec.getName()), codecFactory.decodingFactory(spec)));
	}

	public void subscribe(ChannelName name, CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, C callback) {
		subscribe(name, commit, commit, error, callback);
	}

	public void subscribe(ChannelName name, Object handle, CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, C callback) {
		ChannelListener listener = listenerFactory.create(commit, error, callback);
		channels.subscribe(name, handle, listener);
	}

	public <K, V> void subscribe(ChannelSpec<K, V> spec, CommitListener<K, V> commit, ErrorListener error, C callback) {
		CommitListener<ReadBuffer, ReadBuffer> decoded = codecFactory.decodingFactory(spec).create(commit, error);
		subscribe(spec.getName(), commit, decoded, error, callback);
	}

	public void unsubscribe(ChannelName name, Object handle) {
		channels.unsubscribe(name, handle);
	}

	public Consumer<Object> unsubscriber(ChannelName name) {
		Require.nonNull(name);
		return h -> unsubscribe(name, h);
	}
}
