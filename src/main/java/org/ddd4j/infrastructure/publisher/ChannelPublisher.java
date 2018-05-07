package org.ddd4j.infrastructure.publisher;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.ddd4j.infrastructure.channel.Channels.DecodingFactory;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing.Closeable;

public class ChannelPublisher<C> implements Closeable {

	@FunctionalInterface
	public interface ListenerFactory<C> {

		ChannelListener create(CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, C callback);

		default ListenerFactory<C> wrapped(UnaryOperator<ChannelListener> wrapper) {
			Require.nonNull(wrapper);
			return (commit, error, callback) -> wrapper.apply(create(commit, error, callback));
		}
	}

	private final SubscribedChannels channels;
	private final ListenerFactory<C> listenerFactory;

	public ChannelPublisher(SubscribedChannels channels, ListenerFactory<C> listenerFactory) {
		this.channels = Require.nonNull(channels);
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

	public <K, V> Publisher<K, V, C> publisher(ChannelName name, DecodingFactory<K, V> decodingFactory) {
		Consumer<Object> unsubscriber = unsubscriber(name);
		return (s, c) -> channels.subscribe(name, s, FlowSubscription.createListener(listenerFactory, c, s, unsubscriber, decodingFactory));
	}

	public void subscribe(ChannelName name, CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, C callback) {
		subscribe(name, commit, commit, error, callback);
	}

	public void subscribe(ChannelName name, Object handle, CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, C callback) {
		ChannelListener listener = listenerFactory.create(commit, error, callback);
		channels.subscribe(name, handle, listener);
	}

	public void unsubscribe(ChannelName name, Object handle) {
		channels.unsubscribe(name, handle);
	}

	public Consumer<Object> unsubscriber(ChannelName name) {
		Require.nonNull(name);
		return h -> unsubscribe(name, h);
	}
}
