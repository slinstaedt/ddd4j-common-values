package org.ddd4j.infrastructure.channel.kafka;

import java.time.Instant;
import java.time.ZoneOffset;
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
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.ddd4j.Require;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RebalanceListener;
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
import org.ddd4j.util.Lazy;
import org.ddd4j.util.Sequence;
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

	static final ZoneOffset ZONE_OFFSET = ZoneOffset.UTC; // TODO

	private static final ByteArrayDeserializer DESERIALIZER = new ByteArrayDeserializer();
	private static final ByteArraySerializer SERIALIZER = new ByteArraySerializer();

	static ProducerRecord<byte[], byte[]> convert(ChannelName name, Recorded<ReadBuffer, ReadBuffer> recorded) {
		int partition = recorded.partition(ReadBuffer::hash); // TODO partionCount needed
		long timestamp = recorded.getTimestamp().toEpochMilli();
		byte[] key = recorded.getKey().toByteArray();
		byte[] value = recorded.getValue().toByteArray();
		List<Header> headers = new ArrayList<>();
		recorded.getHeaders().forEach((k, v) -> headers.add(new RecordHeader(k, v.toByteArray())));
		return new ProducerRecord<>(name.value(), partition, timestamp, key, value, headers);
	}

	static Committed<ReadBuffer, ReadBuffer> convert(ConsumerRecord<byte[], byte[]> record) {
		ReadBuffer key = Bytes.wrap(record.key()).buffered();
		ReadBuffer value = Bytes.wrap(record.value()).buffered();
		Revision actual = new Revision(record.partition(), record.offset());
		Revision next = actual.increment(1);
		Instant timestamp = Instant.ofEpochMilli(record.timestamp());
		Map<String, ReadBuffer> headers = new HashMap<>();
		record.headers().forEach(h -> headers.put(h.key(), Bytes.wrap(h.value()).buffered()));
		return DataAccessFactory.committed(key, value, actual, next, timestamp, headers);
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
	public void closeChecked() throws Exception {
		producer.closeChecked();
		hotConsumer.closeChecked();
	}

	@Override
	public ColdSource createColdSource(CommitListener<ReadBuffer, ReadBuffer> commit, CompletionListener completion, ErrorListener error) {
		Scheduler scheduler = context.get(Scheduler.KEY);
		Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(propsFor(null, 200));
		// TODO
		return null;
	}

	@Override
	public Committer<ReadBuffer, ReadBuffer> createCommitter(ChannelName name) {
		return new KafkaCommitter(context.get(Scheduler.KEY), producer.get(), name);
	}

	@Override
	public HotSource createHotSource(CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, RebalanceListener rebalance) {
		Scheduler scheduler = context.get(Scheduler.KEY);
		Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(propsFor(null, 200));
		return new KafkaHotSource(scheduler, consumer, commit, error, rebalance);
	}

	@Override
	public Writer<ReadBuffer, ReadBuffer> createWriter(ChannelName name) {
		return new KafkaWriter(context.get(Scheduler.KEY), producer.get(), name);
	}

	@Override
	public Map<ChannelName, Integer> knownChannelNames() {
		// TODO
		return hotConsumer.get().listTopics().entrySet().stream().collect(
				Collectors.toMap(e -> ChannelName.of(e.getKey()), e -> e.getValue().size()));
	}
}
