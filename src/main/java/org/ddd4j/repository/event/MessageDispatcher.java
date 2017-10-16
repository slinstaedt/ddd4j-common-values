package org.ddd4j.repository.event;

import java.util.Set;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.ddd4j.Require;
import org.ddd4j.Throwing.Closeable;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.SchemaCodec;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelSpec;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.repository.FlowListener;
import org.ddd4j.value.versioned.Committed;

public class MessageDispatcher<C> implements Closeable {

	public interface ListenerFactory<C> {

		ChannelListener create(CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, C callback);

		default ListenerFactory<C> wrapListener(UnaryOperator<ChannelListener> wrapper) {
			Require.nonNull(wrapper);
			return (commit, error, callback) -> wrapper.apply(create(commit, error, callback));
		}
	}

	private final SubscribedChannels channels;
	private final ListenerFactory<C> factory;

	public MessageDispatcher(SubscribedChannels channels, ListenerFactory<C> factory) {
		this.channels = Require.nonNull(channels);
		this.factory = Require.nonNull(factory);
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

	public Promise<Integer> subscribe(ChannelName name, Object handle, CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error,
			C callback) {
		ChannelListener listener = factory.create(commit, error, callback);
		return channels.subscribe(name, handle, listener);
	}

	public Promise<Integer> subscribe(ChannelName name, CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, C callback) {
		return subscribe(name, commit, commit, error, callback);
	}

	public <K, V> void subscribe(SchemaCodec.Factory factory, ChannelSpec<K, V> spec, CommitListener<K, V> commit, ErrorListener error,
			C callback) {
		SchemaCodec.Decoder<V> decoder = factory.decoder(spec);
		CommitListener<ReadBuffer, ReadBuffer> mappedListener = commit.mapPromised(spec::deserializeKey, decoder::decode, error);
		subscribe(spec.getName(), commit, mappedListener, error, callback);
	}

	public Flow.Publisher<Committed<ReadBuffer, ReadBuffer>> subscriber(ChannelName name, C callback) {
		Require.nonNullElements(name, callback);
		return s -> {
			FlowListener<ReadBuffer, ReadBuffer> listener = new FlowListener<>(s, unsubscriber(name));
			subscribe(name, s, listener, listener, callback);
		};
	}

	public void unsubscribe(ChannelName name, Object handle) {
		channels.unsubscribe(name, handle);
	}

	public Consumer<Object> unsubscriber(ChannelName name) {
		Require.nonNull(name);
		return h -> unsubscribe(name, h);
	}
}
