package org.ddd4j.infrastructure.channel.kafka;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.Promise.Cancelable;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.spi.ColdSource;
import org.ddd4j.infrastructure.domain.ChannelRevisions;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.infrastructure.domain.value.ChannelRevision;
import org.ddd4j.infrastructure.domain.value.CommittedRecords;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.ScheduledTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Sequence;

public class KafkaColdSource implements ColdSource, ScheduledTask {

	private static final ConsumerRecords<byte[], byte[]> EMPTY_RECORDS = ConsumerRecords.empty();
	private static final Map<Mode, BiConsumer<Consumer<?, ?>, List<TopicPartition>>> FLOWS = Map.of(Mode.PAUSE, Consumer::pause,
			Mode.RESUME, Consumer::resume);

	private final Agent<Consumer<byte[], byte[]>> client;
	private final CommitListener<ReadBuffer, ReadBuffer> commit;
	private final ErrorListener error;
	private final CompletionListener completion;
	private final ChannelRevisions state;
	private final Rescheduler rescheduler;

	KafkaColdSource(Scheduler scheduler, Consumer<byte[], byte[]> consumer, CommitListener<ReadBuffer, ReadBuffer> commit,
			ErrorListener error, CompletionListener completion) {
		this.client = scheduler.createAgent(consumer);
		this.commit = Require.nonNull(commit);
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
	public Cancelable<?> controlFlow(Mode mode, Sequence<ChannelPartition> values) {
		return client.execute(c -> FLOWS.get(mode).accept(c, values.map(cp -> cp.to(TopicPartition::new)).toList()));
	}

	@Override
	public Promise<Trigger> onScheduled(Scheduler scheduler) {
		return client.performBlocked((t, u) -> c -> c.assignment().isEmpty() ? EMPTY_RECORDS : c.poll(u.toMillis(t)))
				.thenApply(KafkaChannelFactory::convert)
				.on(CommittedRecords::isEmpty, this::checkCompleteness)
				.whenCompleteSuccessfully(cr -> cr.forEach(state::tryUpdate))
				.whenCompleteSuccessfully(cr -> cr.forEach(commit::onNext))
				.whenCompleteExceptionally(error::onError)
				.thenReturn(this::triggering);
	}

	@Override
	public Promise<ChannelRevision> revision(ChannelPartition partition, Instant timestamp, Direction direction) {
		Duration diff = Duration.ofHours(12);
		TopicPartition tp = partition.to(TopicPartition::new);
		return client.performBlocked((t, u) -> c -> c.offsetsForTimes(Collections.singletonMap(tp, timestamp.toEpochMilli())).get(tp))//
				.on(ots -> direction.check(timestamp, ots.timestamp()),
						p -> p.thenApply(ots -> new ChannelRevision(partition, ots.offset())),
						p -> revision(partition, direction.apply(timestamp, diff), direction));
	}

	@Override
	public Promise<?> start(Sequence<ChannelRevision> revisions) {
		state.add(revisions);
		Promise<?> promise = client.execute(c -> {
			c.assign(state.toList(TopicPartition::new));
			state.forEach(r -> c.seek(r.to(TopicPartition::new), r.getOffset()));
		});
		rescheduler.doIfNecessary();
		return promise;
	}

	@Override
	public Promise<?> stop(Sequence<ChannelPartition> partitions) {
		state.remove(partitions);
		return client.execute(c -> c.assign(state.toList(TopicPartition::new)));
	}

	private Trigger triggering() {
		return !state.isEmpty() ? Trigger.NOTHING : Trigger.RESCHEDULE;
	}
}
