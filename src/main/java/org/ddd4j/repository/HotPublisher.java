package org.ddd4j.repository;

import java.util.Map;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.SchemaCodec;
import org.ddd4j.infrastructure.channel.SchemaCodec.Decoder;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.channel.spi.DataAccessFactory;
import org.ddd4j.infrastructure.channel.spi.HotSource;
import org.ddd4j.infrastructure.channel.spi.HotSource.Callback;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelSpec;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;

//TODO needed?
public class HotPublisher {

	public static class Factory implements DataAccessFactory {

		private final Context context;

		public Factory(Context context) {
			this.context = Require.nonNull(context);
		}

		public HotPublisher createHotPublisher(Callback callback) {
			return new HotPublisher(context.get(Scheduler.KEY), context.get(SchemaCodec.FACTORY), context.get(HotSource.FACTORY), callback);
		}

		@Override
		public Map<ChannelName, Integer> knownChannelNames() {
			return context.get(HotSource.FACTORY).knownChannelNames();
		}
	}

	public static final Key<Factory> FACTORY = Key.of(Factory.class, Factory::new);

	private final SchemaCodec.Factory codecFactory;
	private final HotSource source;
	private final ChannelPublisher publisher;

	public HotPublisher(Scheduler scheduler, SchemaCodec.Factory codecFactory, HotSource.Factory sourceFactory,
			HotSource.Callback callback) {
		this.codecFactory = Require.nonNull(codecFactory);
		this.publisher = new ChannelPublisher(scheduler, this::onSubscribed, this::onUnsubscribed);
		this.source = sourceFactory.createHotSource(publisher, publisher);
	}

	private Promise<Integer> onSubscribed(ChannelName name) {
		return source.subscribe(name);
	}

	private void onUnsubscribed(ChannelName name) {
		source.unsubscribe(name);
	}

	public Promise<Integer> subscribe(ChannelName name, SourceListener<ReadBuffer, ReadBuffer> listener) {
		return publisher.subscribe(name, listener);
	}

	public <K, V> Promise<Integer> subscribe(ChannelSpec<K, V> spec, SourceListener<K, V> source, ErrorListener error) {
		Decoder<V> decoder = codecFactory.decoder(spec);
		return subscribe(spec.getName(), source.mapPromised(spec::deserializeKey, decoder::decode, error));
	}

	public void unsubscribe(ChannelName name, SourceListener<ReadBuffer, ReadBuffer> listener) {
		publisher.unsubscribe(name, listener);
	}
}