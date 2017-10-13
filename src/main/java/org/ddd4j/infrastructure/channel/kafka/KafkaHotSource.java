package org.ddd4j.infrastructure.channel.kafka;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.Require;
import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RepartitioningListener;
import org.ddd4j.infrastructure.channel.spi.HotSource;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.ScheduledTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;

public class KafkaHotSource implements HotSource, ScheduledTask {

	private static class KafkaRebalanceCallback implements ConsumerRebalanceListener {

		private static Sequence<ChannelPartition> toChannelPartitions(Collection<TopicPartition> partitions) {
			return Sequence.ofCopied(partitions).map(tp -> new ChannelPartition(tp.topic(), tp.partition()));
		}

		private final Consumer<byte[], byte[]> consumer;
		private final CommitListener<?, ?> commit;
		private final ErrorListener error;
		private final RepartitioningListener repartitioning;

		KafkaRebalanceCallback(Consumer<byte[], byte[]> consumer, CommitListener<?, ?> commit, ErrorListener error,
				RepartitioningListener repartitioning) {
			this.consumer = Require.nonNull(consumer);
			this.commit = Require.nonNull(commit);
			this.error = Require.nonNull(error);
			this.repartitioning = Require.nonNull(repartitioning);
		}

		void onError(Throwable throwable) {
			error.onError(throwable);
		}

		@Override
		public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
			consumer.seekToEnd(partitions);
			repartitioning.onPartitionsAssigned(toChannelPartitions(partitions));
		}

		@Override
		public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
			repartitioning.onPartitionsRevoked(toChannelPartitions(partitions));
		}
	}

	private static final ConsumerRecords<byte[], byte[]> EMPTY_RECORDS = ConsumerRecords.empty();

	private final CommitListener<ReadBuffer, ReadBuffer> commit;
	private final KafkaRebalanceCallback callback;
	private final Agent<Consumer<byte[], byte[]>> client;
	private final Rescheduler rescheduler;
	private final Map<String, Promise<Integer>> subscriptions;

	KafkaHotSource(Scheduler scheduler, Consumer<byte[], byte[]> consumer, CommitListener<ReadBuffer, ReadBuffer> commit,
			ErrorListener error, RepartitioningListener repartitioning) {
		this.commit = Require.nonNull(commit);
		this.callback = new KafkaRebalanceCallback(consumer, commit, error, repartitioning);
		this.client = scheduler.createAgent(consumer);
		this.rescheduler = scheduler.reschedulerFor(this);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	@Override
	public void closeChecked() throws Exception {
		client.executeBlocked((t, u) -> c -> c.close(t, u)).join();
	}

	@Override
	public Promise<Trigger> onScheduled(Scheduler scheduler) {
		return client.performBlocked((t, u) -> c -> c.subscription().isEmpty() ? EMPTY_RECORDS : c.poll(u.toMillis(t)))
				.whenComplete(rs -> rs.forEach(this::onNext), callback::onError)
				.thenReturn(this::triggering);
	}

	private void onNext(ConsumerRecord<byte[], byte[]> record) {
		commit.onNext(ChannelName.of(record.topic()), KafkaChannelFactory.convert(record));
	}

	@Override
	public Promise<Integer> subscribe(ChannelName name) {
		return subscriptions.computeIfAbsent(name.value(), this::subscribe);
	}

	private Promise<Integer> subscribe(String topic) {
		Promise<Integer> partitionSize = client.perform(c -> c.partitionsFor(topic).size());
		partitionSize.whenCompleteExceptionally(callback::onError);
		updateSubscription();
		rescheduler.doIfNecessary();
		return partitionSize;
	}

	private Trigger triggering() {
		return !subscriptions.isEmpty() ? Trigger.NOTHING : Trigger.RESCHEDULE;
	}

	@Override
	public void unsubscribe(ChannelName name) {
		if (subscriptions.remove(name.value()) != null) {
			updateSubscription();
		}
	}

	private void updateSubscription() {
		client.execute(c -> c.subscribe(subscriptions.keySet(), callback));
	}
}
