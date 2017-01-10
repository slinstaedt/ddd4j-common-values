package org.ddd4j.infrastructure.source.kafka;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.scheduler.LoopedTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.infrastructure.source.HotSource;
import org.ddd4j.infrastructure.source.Subscriber;
import org.ddd4j.value.versioned.Revisions;

public class KafkaHotSource implements HotSource, LoopedTask, ConsumerRebalanceListener {

	private Scheduler scheduler;
	private Consumer<byte[], byte[]> client;
	private final Map<String, List<PartitionInfo>> partitionInfos;
	private Map<String, Map<Subscriber, Revisions>> subscriptions;

	public KafkaHotSource() {
		partitionInfos = client.listTopics();
	}

	@Override
	public void loop(long duration, TimeUnit unit) throws Exception {
		ConsumerRecords<byte[], byte[]> records = client.poll(unit.toMillis(duration));
	}

	@Override
	public Subscription subscribe(ResourceDescriptor topic, Subscriber subscriber) {
		Revisions revisions = new Revisions(partitionInfos.get(topic.value()).size());
		subscriptions.computeIfAbsent(topic.value(), this::subscribeTopic).putIfAbsent(subscriber, revisions);
		return () -> unsubscribe(subscriber);
	}

	private void unsubscribe(Subscriber subscriber) {
		Iterator<Map<Subscriber, Revisions>> iterator = subscriptions.values().iterator();
		while (iterator.hasNext()) {
			Map<Subscriber, Revisions> subscribers = iterator.next();
			subscribers.remove(subscriber);
			if (subscribers.isEmpty()) {
				iterator.remove();
			}
		}
		client.subscribe(subscriptions.keySet(), this);
	}

	private Map<Subscriber, Revisions> subscribeTopic(String topic) {
		HashSet<String> topics = new HashSet<>(subscriptions.keySet());
		topics.add(topic);
		client.subscribe(topics, this);
		return new ConcurrentHashMap<>();
	}

	@Override
	public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
		for (TopicPartition tp : partitions) {
			for (Entry<Subscriber, Revisions> entry : subscriptions.getOrDefault(tp.topic(), Collections.emptyMap()).entrySet()) {
				entry.setValue(entry.getKey().loadRevisions());
			}
		}
	}

	@Override
	public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
		Map<String, Set<Integer>> partitionPerTopic = partitions.stream()
				.collect(groupingBy(TopicPartition::topic, mapping(TopicPartition::partition, toSet())));
		for (Entry<String, Set<Integer>> entry : partitionPerTopic.entrySet()) {
			subscriptions.getOrDefault(entry.getKey(), Collections.emptyMap()).entrySet().forEach(e -> e.getKey().saveRevisions(e.getValue()));
		}
	}
}
