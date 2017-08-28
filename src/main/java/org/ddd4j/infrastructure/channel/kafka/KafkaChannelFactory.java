package org.ddd4j.infrastructure.channel.kafka;

import java.util.Properties;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.ddd4j.Require;
import org.ddd4j.infrastructure.channel.ColdSource;
import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.infrastructure.channel.util.SourceListener;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.spi.ServiceBinder;
import org.ddd4j.spi.ServiceConfigurer;
import org.ddd4j.value.collection.Seq;

public class KafkaChannelFactory implements ColdSource.Factory, HotSource.Factory {

	public static class Configurer implements ServiceConfigurer {

		@Override
		public void bindServices(ServiceBinder binder) {
			binder.bind(ColdSource.FACTORY).toDelegate(KafkaChannelFactory.KEY);
			binder.bind(HotSource.FACTORY).toDelegate(KafkaChannelFactory.KEY);
		}
	}

	public static final Key<KafkaChannelFactory> KEY = Key.of(KafkaChannelFactory.class, KafkaChannelFactory::new);

	static Properties propsFor(Seq<String> servers, int timeout) {
		Properties props = new Properties();
		props.setProperty("bootstrap.servers", String.join(",", servers));
		props.setProperty("group.id", null);
		props.setProperty("enable.auto.commit", "false");
		props.setProperty("heartbeat.interval.ms", String.valueOf(timeout / 4));
		props.setProperty("session.timeout.ms", String.valueOf(timeout));
		return props;
	}

	private final Context context;

	public KafkaChannelFactory(Context context) {
		this.context = Require.nonNull(context);
	}

	@Override
	public ColdSource createColdSource(ColdSource.Callback callback, SourceListener<ReadBuffer, ReadBuffer> listener) {
		Scheduler scheduler = context.get(Scheduler.KEY);
		Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(propsFor(null, 200));
		// TODO
		return null;
	}

	@Override
	public HotSource createHotSource(HotSource.Callback callback, SourceListener<ReadBuffer, ReadBuffer> listener) {
		Scheduler scheduler = context.get(Scheduler.KEY);
		Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(propsFor(null, 200));
		return new KafkaHotSource(scheduler, consumer, callback, listener);
	}
}
