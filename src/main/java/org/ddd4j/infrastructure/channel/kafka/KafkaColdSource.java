package org.ddd4j.infrastructure.channel.kafka;

import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.Require;
import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.channel.spi.ColdSource;
import org.ddd4j.infrastructure.domain.ChannelRevisions;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.infrastructure.domain.value.ChannelRevision;
import org.ddd4j.infrastructure.domain.value.CommittedRecords;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.ScheduledTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;

public class KafkaColdSource implements ColdSource, ScheduledTask {

	private static final ConsumerRecords<byte[], byte[]> EMPTY_RECORDS = ConsumerRecords.empty();

	private final Agent<Consumer<byte[], byte[]>> client;
	private final SourceListener<ReadBuffer, ReadBuffer> source;
	private final ErrorListener error;
	private final CompletionListener completion;
	private final ChannelRevisions state;
	private final Rescheduler rescheduler;

	KafkaColdSource(Scheduler scheduler, Consumer<byte[], byte[]> consumer, SourceListener<ReadBuffer, ReadBuffer> source,
			ErrorListener error, CompletionListener completion) {
		this.client = scheduler.createAgent(consumer);
		this.source = Require.nonNull(source);
		this.error = Require.nonNull(error);
		this.completion = Require.nonNull(completion);
		this.state = new ChannelRevisions();
		this.rescheduler = scheduler.reschedulerFor(this);
	}

	private Promise<CommittedRecords> checkCompleteness(Promise<CommittedRecords> promise) {
		promise.thenCompose(rs -> client.performBlocked((t,
				u) -> c -> state.filter(c.endOffsets(state.toList(TopicPartition::new))
						.entrySet()
						.stream()
						.map(e -> new ChannelRevision(e.getKey().topic(), e.getKey().partition(), e.getValue()))
						.collect(Collectors.toList())::contains)))
				.on(Sequence::isNotEmpty, p -> p.thenRun(completion::onComplete));
		return promise;
	}

	@Override
	public void closeChecked() {
		state.clear();
		client.execute(Consumer::unsubscribe);
		client.executeBlocked((t, u) -> c -> c.close(t, u)).join();
	}

	@Override
	public Promise<Trigger> onScheduled(Scheduler scheduler) {
		return client.performBlocked((t, u) -> c -> c.assignment().isEmpty() ? EMPTY_RECORDS : c.poll(u.toMillis(t)))
				.thenApply(KafkaChannelFactory::convert)
				.on(CommittedRecords::isEmpty, this::checkCompleteness)
				.whenCompleteSuccessfully(cr -> cr.forEach(state::tryUpdate))
				.whenCompleteSuccessfully(cr -> cr.forEach(source::onNext))
				.whenCompleteExceptionally(error::onError)
				.thenReturn(this::triggering);
	}

	private Trigger triggering() {
		return !state.isEmpty() ? Trigger.NOTHING : Trigger.RESCHEDULE;
	}

	@Override
	public void resume(Sequence<ChannelRevision> revisions) {
		this.state.add(revisions);
		client.execute(c -> {
			c.assign(this.state.toList(TopicPartition::new));
			this.state.forEach(r -> c.seek(r.to(TopicPartition::new), r.getOffset()));
		});
		rescheduler.doIfNecessary();
	}

	@Override
	public void stop(Sequence<ChannelPartition> partitions) {
		state.remove(partitions);
		client.execute(c -> c.assign(state.toList(TopicPartition::new)));
	}
}
