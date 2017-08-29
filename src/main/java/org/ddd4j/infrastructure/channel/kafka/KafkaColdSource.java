package org.ddd4j.infrastructure.channel.kafka;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.Sequence;
import org.ddd4j.infrastructure.channel.ColdSource;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.domain.ChannelRevision;
import org.ddd4j.infrastructure.channel.util.SourceListener;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.ScheduledTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;

public class KafkaColdSource implements ColdSource, ScheduledTask {

	private static final ConsumerRecords<byte[], byte[]> EMPTY_RECORDS = ConsumerRecords.empty();

	private final Agent<Consumer<byte[], byte[]>> client;
	private final Rescheduler rescheduler;
	private final Map<String, Promise<Integer>> subscriptions;
	private final Callback callback;
	private final SourceListener<ReadBuffer, ReadBuffer> listener;

	KafkaColdSource(Scheduler scheduler, Consumer<byte[], byte[]> consumer, Callback callback,
			SourceListener<ReadBuffer, ReadBuffer> listener) {
		this.client = scheduler.createAgent(consumer);
		this.callback = callback;
		this.listener = listener;
		this.rescheduler = scheduler.reschedulerFor(this);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	@Override
	public void closeChecked() throws Exception {
		client.executeBlocked((t, u) -> c -> c.close(t, u)).join();
	}

	@Override
	public Promise<Trigger> onScheduled(Scheduler scheduler) {
		return client.performBlocked((t, u) -> c -> c.assignment().isEmpty() ? EMPTY_RECORDS : c.poll(u.toMillis(t)))
				.whenComplete(rs -> rs.forEach(r -> listener.onNext(ChannelName.of(r.topic()), KafkaChannelFactory.convert(r))),
						callback::onError)
				.thenReturn(this::triggering);
	}

	private Trigger triggering() {
		return !subscriptions.isEmpty() ? Trigger.NOTHING : Trigger.RESCHEDULE;
	}

	@Override
	public void pause(Sequence<ChannelRevision> revisions) {
		// TODO Auto-generated method stub

	}

	@Override
	public void resume(Sequence<ChannelRevision> revisions) {
		// TODO Auto-generated method stub
		List<TopicPartition> partitions = revisions.map(r -> new TopicPartition(r.getName().value(), r.getPartition())).toList();
		client.execute(c -> c.assign(partitions));
	}
}
