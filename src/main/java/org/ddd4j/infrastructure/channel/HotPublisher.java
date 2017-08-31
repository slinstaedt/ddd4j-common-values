package org.ddd4j.infrastructure.channel;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.HotSource.Callback;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.domain.ChannelSpec;
import org.ddd4j.infrastructure.channel.util.SchemaCodec;
import org.ddd4j.infrastructure.channel.util.SchemaCodec.Decoder;
import org.ddd4j.infrastructure.channel.util.SourceListener;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;

public class HotPublisher {

	public static class Factory implements DataAccessFactory {

		private final Context context;

		public Factory(Context context) {
			this.context = Require.nonNull(context);
		}

		public HotPublisher createHotPublisher(Callback callback) {
			return new HotPublisher(context.get(SchemaCodec.FACTORY), context.get(HotSource.FACTORY), callback);
		}

		@Override
		public Map<ChannelName, Integer> knownChannelNames() {
			return context.get(HotSource.FACTORY).knownChannelNames();
		}
	}

	private static class Listeners {

		private final ChannelName name;
		private final Promise<Integer> partitionSize;
		private final Runnable closer;
		private final Map<Object, SourceListener<ReadBuffer, ReadBuffer>> listeners;

		public Listeners(ChannelName name, Promise<Integer> partitionSize, Runnable closer) {
			this.name = Require.nonNull(name);
			this.partitionSize = Require.nonNull(partitionSize);
			this.closer = Require.nonNull(closer);
			this.listeners = new ConcurrentHashMap<>();
		}

		public HotPublisher.Listeners add(SourceListener<ReadBuffer, ReadBuffer> listener) {
			return add(listener, listener);
		}

		public HotPublisher.Listeners add(Object handle, SourceListener<ReadBuffer, ReadBuffer> listener) {
			listeners.put(Require.nonNull(handle), Require.nonNull(listener));
			return this;
		}

		public void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
			listeners.values().forEach(l -> l.onNext(name, DataAccessFactory.resetBuffers(committed)));
		}

		public Promise<Integer> partitionSize() {
			return partitionSize;
		}

		public HotPublisher.Listeners remove(Object handle) {
			listeners.remove(handle);
			if (listeners.isEmpty()) {
				closer.run();
				return null;
			} else {
				return this;
			}
		}
	}

	private static class Subscriptions {

		private static final HotPublisher.Listeners NONE = new Listeners(ChannelName.of("<NONE>"), Promise.failed(new AssertionError()),
				Void.class::getClass);

		private final Function<ChannelName, HotPublisher.Listeners> onSubscribe;
		private final ConcurrentMap<ChannelName, HotPublisher.Listeners> listeners;

		public Subscriptions(Function<ChannelName, HotPublisher.Listeners> onSubscribe) {
			this.onSubscribe = Require.nonNull(onSubscribe);
			this.listeners = new ConcurrentHashMap<>();
		}

		public boolean isEmpty() {
			return listeners.isEmpty();
		}

		public void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
			listeners.getOrDefault(name, NONE).onNext(committed);
		}

		public Promise<Integer> subscribe(ChannelName name, SourceListener<ReadBuffer, ReadBuffer> listener) {
			return listeners.computeIfAbsent(name, onSubscribe).add(listener).partitionSize();
		}

		public Set<ChannelName> channels() {
			return Collections.unmodifiableSet(listeners.keySet());
		}

		public void unsubscribe(ChannelName name, SourceListener<?, ?> listener) {
			listeners.computeIfPresent(name, (r, s) -> s.remove(listener));
		}
	}

	public static final Key<Factory> FACTORY = Key.of(Factory.class, Factory::new);

	private final SchemaCodec.Factory codecFactory;
	private final HotSource source;
	private final Subscriptions subscriptions;

	public HotPublisher(SchemaCodec.Factory codecFactory, HotSource.Factory sourceFactory, HotSource.Callback callback) {
		this.codecFactory = Require.nonNull(codecFactory);
		this.source = sourceFactory.createHotSource(callback, this::onNext);
		this.subscriptions = new Subscriptions(this::onSubscribe);
	}

	private void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
		subscriptions.onNext(name, committed);
	}

	private Listeners onSubscribe(ChannelName name) {
		return new Listeners(name, source.subscribe(name), () -> source.unsubscribe(name));
	}

	public Promise<Integer> subscribe(ChannelName name, SourceListener<ReadBuffer, ReadBuffer> listener) {
		return subscriptions.subscribe(name, listener);
	}

	public <K, V> Promise<Integer> subscribe(ChannelSpec<K, V> spec, SourceListener<K, V> listener) {
		Decoder<V> decoder = codecFactory.decoder(spec);
		return subscribe(spec.getName(), listener.mapPromised(spec::deserializeKey, decoder::decode));
	}

	public boolean isSubcribed() {
		return !subscriptions.isEmpty();
	}

	public Set<ChannelName> subscribed() {
		return subscriptions.channels();
	}

	public void unsubscribe(ChannelName name, SourceListener<?, ?> listener) {
		subscriptions.unsubscribe(name, listener);
	}
}