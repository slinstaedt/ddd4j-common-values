package org.ddd4j.infrastructure.channel.kafka;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.PartitionInfo;
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

	private static Committed<ReadBuffer, ReadBuffer> convert(ConsumerRecord<byte[], byte[]> record) {
		ReadBuffer key = Bytes.wrap(record.key()).buffered();
		ReadBuffer value = Bytes.wrap(record.value()).buffered();
		Revision actual = new Revision(record.partition(), record.offset());
		Revision next = actual.increment(1);
		ZonedDateTime timestamp = Instant.ofEpochMilli(record.timestamp()).atZone(ZoneOffset.UTC);
		// TODO deserialize header?
		Props header = new Props(value);
		return new Committed<>(key, value, actual, next, timestamp, header);
	}

	static Revisions currentRevisions(Consumer<?, ?> client, String topic) {
		List<PartitionInfo> infos = client.partitionsFor(topic);
		List<TopicPartition> partitions = infos.stream().map(i -> new TopicPartition(i.topic(), i.partition())).collect(Collectors.toList());
		Map<TopicPartition, Long> offsets = client.endOffsets(partitions);
		return offsets.entrySet().stream().reduce(new Revisions(offsets.size()), (r, e) -> r.withPartition(e.getKey().partition(), e.getValue()),
				Revisions::with);
	}

	private final Agent<Consumer<byte[], byte[]>> client;
	private final ChannelListener listener;
	private final Map<String, Revisions> subscriptions;

	public KafkaCallback(Scheduler scheduler, Consumer<byte[], byte[]> consumer, ChannelListener listener) {
		this.client = scheduler.createAgent(consumer);
		this.listener = Require.nonNull(listener);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	@Override
	public void closeChecked() {
		client.perform(Consumer::close);
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

	public int partitionSize(ResourceDescriptor topic) {
		// TODO Auto-generated method stub
		client.execute(c -> c.partitionsFor(topic.value()).size());
		return 0;
	}

	@Override
	public Promise<Trigger> scheduleWith(Executor executor, long timeout, TimeUnit unit) {
		return client.execute(c -> c.subscription().isEmpty() ? EMPTY_RECORDS : c.poll(unit.toMillis(timeout)))
				.sync()
				.whenCompleteSuccessfully(rs -> rs.forEach(r -> listener.onNext(ResourceDescriptor.of(r.topic()), convert(r))))
				.whenCompleteExceptionally(listener::onError)
				.handleSuccess(rs -> rs == EMPTY_RECORDS ? Trigger.NOTHING : Trigger.RESCHEDULE);
	}

	@Override
	public Promise<Revisions> seek(ResourceDescriptor topic, Revision revision) {
		Promise<Revisions> revisions = subscribe(topic);
		client.perform(c -> {
			subscriptions.computeIfPresent(topic.value(), (t, r) -> r.with(revision));
			c.seek(new TopicPartition(topic.value(), revision.getPartition()), revision.getOffset());
		});
		return revisions;
	}

	@Override
	public Promise<Revisions> subscribe(ResourceDescriptor topic) {
		Revisions revisions = subscriptions.get(topic.value());
		if (revisions != null && revisions.isPartitionSizeKnown()) {
			return revisions;
		}
		if (subscriptions.putIfAbsent(topic.value(), Revisions.NONE) == null) {
			client.perform(c -> {
				subscriptions.put(topic.value(), currentRevisions(c, topic.value()));
				c.subscribe(subscriptions.keySet(), this);
			});
		}
	}

	@Override
	public void unseek(ResourceDescriptor topic) {
		unsubscribe(topic);
	}

	@Override
	public void unsubscribe(ResourceDescriptor topic) {
		if (subscriptions.remove(topic.value()) != null) {
			client.perform(c -> c.subscribe(subscriptions.keySet(), this));
		}
	}
}
