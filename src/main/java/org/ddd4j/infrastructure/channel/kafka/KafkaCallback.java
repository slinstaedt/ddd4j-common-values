package org.ddd4j.infrastructure.channel.kafka;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ChannelListener;
import org.ddd4j.infrastructure.channel.ChannelListener.ColdChannelCallback;
import org.ddd4j.infrastructure.channel.ChannelListener.HotChannelCallback;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.BlockingTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.collection.Props;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;

public class KafkaCallback implements ColdChannelCallback, HotChannelCallback, BlockingTask, ConsumerRebalanceListener {

	private static final ConsumerRecords<byte[], byte[]> EMPTY_RECORDS = new ConsumerRecords<>(Collections.emptyMap());

	private final Agent<KafkaConsumer<byte[], byte[]>> client;
	private final ChannelListener listener;
	private Revisions current;

	public KafkaCallback(Scheduler scheduler, KafkaConsumer<byte[], byte[]> consumer, ChannelListener listener) {
		this.client = scheduler.createAgent(consumer);
		this.listener = Require.nonNull(listener);
	}

	@Override
	public void closeChecked() {
		client.perform(KafkaConsumer::close);
	}

	private Committed<ReadBuffer, ReadBuffer> convert(ConsumerRecord<byte[], byte[]> record) {
		ReadBuffer key = Bytes.wrap(record.key()).buffered();
		ReadBuffer value = Bytes.wrap(record.value()).buffered();
		Revision actual = new Revision(record.partition(), record.offset());
		current = current.updateWithPartition(record.partition(), record.offset());
		ZonedDateTime timestamp = Instant.ofEpochMilli(record.timestamp()).atZone(ZoneOffset.UTC);
		Props header = new Props(value);
		return new Committed<>(key, value, actual, current, timestamp, header);
	}

	@Override
	public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
		partitions.stream().map(TopicPartition::topic).distinct().forEach(topic -> {
			IntStream p = partitions.stream().filter(tp -> tp.topic().equals(topic)).mapToInt(TopicPartition::partition);
			listener.onPartitionsAssigned(ResourceDescriptor.of(topic), p);
		});
	}

	@Override
	public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
		partitions.stream().map(TopicPartition::topic).distinct().forEach(topic -> {
			IntStream p = partitions.stream().filter(tp -> tp.topic().equals(topic)).mapToInt(TopicPartition::partition);
			listener.onPartitionsRevoked(ResourceDescriptor.of(topic), p);
		});
	}

	@Override
	public Promise<Trigger> perform(long timeout, TimeUnit unit) throws Exception {
		return client.execute(c -> c.subscription().isEmpty() ? EMPTY_RECORDS : c.poll(unit.toMillis(timeout)))
				.sync()
				.whenCompleteSuccessfully(rs -> rs.forEach(r -> listener.onNext(ResourceDescriptor.of(r.topic()), convert(r))))
				.whenCompleteExceptionally(listener::onError)
				.handleSuccess(rs -> rs == EMPTY_RECORDS ? Trigger.NOTHING : Trigger.RESCHEDULE);
	}

	@Override
	public void subscribe(ResourceDescriptor topic) {
		client.perform(c -> {
			Set<String> subscription = c.subscription();
			if (!subscription.contains(topic.value())) {
				Set<String> result = new HashSet<>(subscription);
				result.add(topic.value());
				c.subscribe(result, this);
			}
		});
	}

	@Override
	public void unsubscribe(ResourceDescriptor topic) {
		client.perform(c -> {
			Set<String> subscription = c.subscription();
			if (subscription.contains(topic.value())) {
				Set<String> result = new HashSet<>(subscription);
				result.remove(topic.value());
				c.subscribe(result, this);
			}
		});
	}

	@Override
	public void seek(ResourceDescriptor topic, Revision revision) {
		subscribe(topic);
		client.perform(c -> c.seek(new TopicPartition(topic.value(), revision.getPartition()), revision.getOffset()));
	}

	@Override
	public void unseek(ResourceDescriptor topic) {
		unsubscribe(topic);
	}
}
