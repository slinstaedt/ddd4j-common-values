package org.ddd4j.infrastructure.channel.kafka;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.ddd4j.collection.Props;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.ColdSource;
import org.ddd4j.infrastructure.channel.DataAccessFactory;
import org.ddd4j.infrastructure.channel.domain.ChannelRevision;
import org.ddd4j.infrastructure.channel.util.SourceListener;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.ScheduledTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public class KafkaColdSource implements ColdSource, ScheduledTask {

	private static final ConsumerRecords<byte[], byte[]> EMPTY_RECORDS = ConsumerRecords.empty();

	static Committed<ReadBuffer, ReadBuffer> convert(ConsumerRecord<byte[], byte[]> record) {
		ReadBuffer key = Bytes.wrap(record.key()).buffered();
		ReadBuffer value = Bytes.wrap(record.value()).buffered();
		Revision actual = new Revision(record.partition(), record.offset());
		Revision next = actual.increment(1);
		ZonedDateTime timestamp = Instant.ofEpochMilli(record.timestamp()).atZone(ZoneOffset.UTC);
		// TODO deserialize header?
		Props header = new Props(value);
		return DataAccessFactory.committed(key, value, actual, next, timestamp, header);
	}

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
		client.execute(Consumer::close).join();
	}

	@Override
	public Promise<Trigger> onScheduled(Scheduler scheduler) {
		return client.performBlocked((t, u) -> c -> c.assignment().isEmpty() ? EMPTY_RECORDS : c.poll(u.toMillis(t)))
				.whenComplete(rs -> rs.forEach(listener::onNext), callback::onError)
				.thenReturn(this::triggering);
	}

	private Trigger triggering() {
		return !subscriptions.isEmpty() ? Trigger.NOTHING : Trigger.RESCHEDULE;
	}

	@Override
	public void pause(Seq<ChannelRevision> revisions) {
		// TODO Auto-generated method stub

	}

	@Override
	public void resume(Seq<ChannelRevision> revisions) {
		// TODO Auto-generated method stub

	}
}
