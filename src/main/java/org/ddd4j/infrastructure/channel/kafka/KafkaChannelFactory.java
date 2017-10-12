package org.ddd4j.infrastructure.channel.kafka;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.ddd4j.Require;
import org.ddd4j.collection.Lazy;
import org.ddd4j.collection.Props;
import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RepartitioningListener;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.channel.spi.ColdSource;
import org.ddd4j.infrastructure.channel.spi.Committer;
import org.ddd4j.infrastructure.channel.spi.DataAccessFactory;
import org.ddd4j.infrastructure.channel.spi.HotSource;
import org.ddd4j.infrastructure.channel.spi.Writer;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.CommittedRecords;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.spi.ServiceBinder;
import org.ddd4j.spi.ServiceConfigurer;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;

public class KafkaChannelFactory implements ColdSource.Factory, HotSource.Factory, Committer.Factory, Writer.Factory {

	public static class Configurer implements ServiceConfigurer {

		@Override
		public void bindServices(ServiceBinder binder) {
			binder.bind(ColdSource.FACTORY).toDelegate(KafkaChannelFactory.KEY);
			binder.bind(HotSource.FACTORY).toDelegate(KafkaChannelFactory.KEY);
		}
	}

	public static final Key<KafkaChannelFactory> KEY = Key.of(KafkaChannelFactory.class, KafkaChannelFactory::new);

	private static final ByteArrayDeserializer DESERIALIZER = new ByteArrayDeserializer();
	private static final ByteArraySerializer SERIALIZER = new ByteArraySerializer();

	static ProducerRecord<byte[], byte[]> convert(ChannelName name, Recorded<ReadBuffer, ReadBuffer> recorded) {
		int partition = recorded.partition(ReadBuffer::hash);
		long timestamp = ZonedDateTime.now().toEpochSecond();
		byte[] key = recorded.getKey().toByteArray();
		byte[] value = recorded.getValue().toByteArray();
		// TODO serialize header?
		recorded.getHeader();
		return new ProducerRecord<>(name.value(), partition, timestamp, key, value);
	}

	static Committed<ReadBuffer, ReadBuffer> convert(ConsumerRecord<byte[], byte[]> record) {
		ReadBuffer key = Bytes.wrap(record.key()).buffered();
		ReadBuffer value = Bytes.wrap(record.value()).buffered();
		Revision actual = new Revision(record.partition(), record.offset());
		Revision next = actual.increment(1);
		OffsetDateTime timestamp = Instant.ofEpochSecond(record.timestamp()).atOffset(ZoneOffset.UTC); // TODO
		Props header = Props.deserialize(value);
		return DataAccessFactory.committed(key, value, actual, next, timestamp, header);
	}

	static CommittedRecords convert(ConsumerRecords<byte[], byte[]> records) {
		Map<ChannelName, List<Committed<ReadBuffer, ReadBuffer>>> values = new HashMap<>(records.count());
		records.forEach(r -> values.computeIfAbsent(ChannelName.of(r.topic()), n -> new ArrayList<>()).add(convert(r)));
		return CommittedRecords.copied(values);
	}

	static Properties propsFor(Sequence<String> servers, int timeout) {
		Properties props = new Properties();
		props.setProperty("bootstrap.servers", String.join(",", servers));
		props.setProperty("group.id", null);
		props.setProperty("enable.auto.commit", "false");
		props.setProperty("heartbeat.interval.ms", String.valueOf(timeout / 4));
		props.setProperty("session.timeout.ms", String.valueOf(timeout));
		return props;
	}

	private final Context context;
	private final Lazy<Producer<byte[], byte[]>> producer;
	private final Lazy<Consumer<byte[], byte[]>> hotConsumer;

	public KafkaChannelFactory(Context context) {
		this.context = Require.nonNull(context);
		this.producer = Lazy.ofCloseable(() -> new KafkaProducer<>(propsFor(null, 200), SERIALIZER, SERIALIZER));
		this.hotConsumer = Lazy.ofCloseable(() -> new KafkaConsumer<>(propsFor(null, 200), DESERIALIZER, DESERIALIZER));
	}

	@Override
	public Map<ChannelName, Integer> knownChannelNames() {
		// TODO
		return hotConsumer.get().listTopics().entrySet().stream().collect(
				Collectors.toMap(e -> ChannelName.of(e.getKey()), e -> e.getValue().size()));
	}

	@Override
	public void closeChecked() throws Exception {
		producer.closeChecked();
		hotConsumer.closeChecked();
	}

	@Override
	public ColdSource createColdSource(SourceListener<ReadBuffer, ReadBuffer> source, CompletionListener completion, ErrorListener error) {
		Scheduler scheduler = context.get(Scheduler.KEY);
		Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(propsFor(null, 200));
		// TODO
		return null;
	}

	@Override
	public HotSource createHotSource(SourceListener<ReadBuffer, ReadBuffer> source, ErrorListener error,
			RepartitioningListener repartitioning) {
		Scheduler scheduler = context.get(Scheduler.KEY);
		Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(propsFor(null, 200));
		return new KafkaHotSource(scheduler, consumer, source, error, repartitioning);
	}

	@Override
	public Committer<ReadBuffer, ReadBuffer> createCommitter(ChannelName name) {
		return new KafkaCommitter(context.get(Scheduler.KEY), producer.get(), name);
	}

	@Override
	public Writer<ReadBuffer, ReadBuffer> createWriter(ChannelName name) {
		return new KafkaWriter(context.get(Scheduler.KEY), producer.get(), name);
	}
}
