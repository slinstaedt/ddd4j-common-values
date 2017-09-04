package org.ddd4j.infrastructure.channel.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.Require;
import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.ChannelRevisions;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.channel.domain.ChannelPartition;
import org.ddd4j.infrastructure.channel.domain.ChannelRecord;
import org.ddd4j.infrastructure.channel.domain.ChannelRevision;
import org.ddd4j.infrastructure.channel.spi.ColdSource;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.ScheduledTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;

public class KafkaColdSource implements ColdSource, ScheduledTask {

	private static final ConsumerRecords<byte[], byte[]> EMPTY_RECORDS = ConsumerRecords.empty();

	private final Agent<Consumer<byte[], byte[]>> client;
	private final Callback callback;
	private final SourceListener<ReadBuffer, ReadBuffer> listener;
	private final ChannelRevisions revisions;
	private final Rescheduler rescheduler;

	KafkaColdSource(Scheduler scheduler, Consumer<byte[], byte[]> consumer, Callback callback,
			SourceListener<ReadBuffer, ReadBuffer> listener) {
		this.client = scheduler.createAgent(consumer);
		this.callback = Require.nonNull(callback);
		this.listener = Require.nonNull(listener);
		this.revisions = new ChannelRevisions();
		this.rescheduler = scheduler.reschedulerFor(this);
	}

	private Promise<Sequence<ChannelRecord>> checkCompleteness(Promise<Sequence<ChannelRecord>> promise) {
		promise.thenCompose(rs -> client.performBlocked(
				(t, u) -> c -> c.endOffsets(revisions.toList(TopicPartition::new)).equals(revisions.toMap(TopicPartition::new))))
				.on(Boolean.TRUE, p -> p.thenRun(callback::onComplete).thenRun(this::close));
		return promise;
	}

	@Override
	public void closeChecked() {
		revisions.clear();
		client.execute(Consumer::unsubscribe);
		client.executeBlocked((t, u) -> c -> c.close(t, u)).join();
	}

	@Override
	public Promise<Trigger> onScheduled(Scheduler scheduler) {
		return client.performBlocked((t, u) -> c -> c.assignment().isEmpty() ? EMPTY_RECORDS : c.poll(u.toMillis(t)))
				.thenApply(rs -> Sequence.of(rs.partitions()::stream)
						.flatMap(tp -> rs.records(tp).stream())
						.map(r -> new ChannelRecord(r.topic(), KafkaChannelFactory.convert(r))))
				.on(Sequence::isEmpty, this::checkCompleteness)
				.whenCompleteSuccessfully(rs -> rs.map(ChannelRecord::getRevision).forEach(revisions::update))
				.whenCompleteSuccessfully(rs -> rs.forEach(r -> r.accept(listener::onNext)))
				.whenCompleteExceptionally(listener::onError)
				.thenReturn(this::triggering);
	}

	private Trigger triggering() {
		return !revisions.isEmpty() ? Trigger.NOTHING : Trigger.RESCHEDULE;
	}

	@Override
	public void pause(Sequence<ChannelPartition> partitions) {
		revisions.remove(partitions);
		client.execute(c -> c.assign(revisions.toList(TopicPartition::new)));
	}

	@Override
	public void resume(Sequence<ChannelRevision> revisions) {
		this.revisions.add(revisions);
		client.execute(c -> {
			c.assign(this.revisions.toList(TopicPartition::new));
			this.revisions.forEach(r -> c.seek(r.to(TopicPartition::new), r.getOffset()));
		});
		rescheduler.doIfNecessary();
	}
}
