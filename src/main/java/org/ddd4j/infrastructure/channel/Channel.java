package org.ddd4j.infrastructure.channel;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.Throwing.Closeable;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;

public class Channel {

	public interface ColdCallback extends Closeable {

		void seek(ResourceDescriptor topic, Revision revision);

		default void updateAssignmentAndWait(Map<ResourceDescriptor, Promise<Revisions>> assignments) {
			Map<ResourceDescriptor, IntStream> values = assignments.entrySet()
					.stream()
					.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().join().partitions()));
			updateAssignment(values);
		}

		void updateAssignment(Map<ResourceDescriptor, IntStream> assignments);
	}

	public interface HotCallback extends Closeable {

		Promise<Integer> partitionSize(ResourceDescriptor topic);

		void updateSubscription(Set<ResourceDescriptor> topics);
	}

	public interface Callback extends Closeable {

		void seek(ResourceDescriptor topic, Revision revision);

		Promise<Integer> subscribe(ResourceDescriptor topic);

		void unseek(ResourceDescriptor topic, int partition);

		void unsubscribe(ResourceDescriptor topic);
	}

	private static class CombinedCallback extends AbstractCallback {

		private volatile boolean subscribed;

		CombinedCallback(Listener listener, ColdChannel coldChannel, HotChannel hotChannel) {
			super(listener, coldChannel, hotChannel);
			this.subscribed = false;
		}

		@Override
		protected void doAssignAndSeek(ResourceDescriptor topic, Revision revision) {
			if (subscribed) {
				updateSubscription();
			} else {
				updateAssignmentAndWait();
			}
			updateSeek(topic, revision);
		}

		@Override
		protected Promise<Revisions> doSubscribe(ResourceDescriptor topic) {
			subscribed = true;
			return super.doSubscribe(topic);
		}

		@Override
		public void unsubscribe(ResourceDescriptor topic) {
			unsubscribe(topic, () -> subscribed = false);
		}
	}

	private static class SeparatedCallback extends AbstractCallback {

		SeparatedCallback(Listener listener, ColdChannel coldChannel, HotChannel hotChannel) {
			super(listener, coldChannel, hotChannel);
		}

		@Override
		protected void doAssignAndSeek(ResourceDescriptor topic, Revision revision) {
			updateAssignmentAndWait();
			updateSeek(topic, revision);
		}
	}

	private static abstract class AbstractCallback implements Callback {

		private class ColdListener implements ColdChannel.Listener {

			@Override
			public void onError(Throwable throwable) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onNext(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
				assignments.get(topic).whenCompleteSuccessfully(r -> r.update(committed.getActual()));
			}
		}

		private class HotListener implements HotChannel.Listener {
		}

		private final Listener listener;
		private final ColdCallback coldDelegate;
		private final HotCallback hotDelegate;
		private final Map<ResourceDescriptor, Promise<Revisions>> assignments;

		protected AbstractCallback(Listener listener, ColdChannel coldChannel, HotChannel hotChannel) {
			this.listener = Require.nonNull(listener);
			this.assignments = new ConcurrentHashMap<>();
			this.coldDelegate = coldChannel.register(new ColdListener())
			this.hotDelegate = hotChannel.register(new HotListener());
		}

		@Override
		public void closeChecked() throws Exception {
			coldDelegate.closeChecked();
			hotDelegate.closeChecked();
		}

		protected abstract void doAssignAndSeek(ResourceDescriptor topic, Revision revision);

		protected void updateAssignmentAndWait() {
			coldDelegate.updateAssignmentAndWait(assignments);
		}

		protected void updateSubscription() {
			hotDelegate.updateSubscription(assignments.keySet());
		}

		protected void updateSeek(ResourceDescriptor topic, Revision revision) {
			coldDelegate.seek(topic, revision);
		}

		protected Promise<Revisions> doFetchPartitionSize(ResourceDescriptor topic) {
			return hotDelegate.partitionSize(topic).sync().handleSuccess(Revisions::new);
		}

		protected Promise<Revisions> doSubscribe(ResourceDescriptor topic) {
			Set<ResourceDescriptor> topics = new HashSet<>(assignments.keySet());
			topics.add(topic);
			hotDelegate.updateSubscription(topics);
			return doFetchPartitionSize(topic);
		}

		@Override
		public void seek(ResourceDescriptor topic, Revision revision) {
			assignments.computeIfAbsent(topic, t -> doFetchPartitionSize(t))
					.whenCompleteSuccessfully(r -> r.update(revision))
					.whenCompleteSuccessfully(r -> doAssignAndSeek(topic, revision));
		}

		@Override
		public Promise<Integer> subscribe(ResourceDescriptor topic) {
			return assignments.computeIfAbsent(topic, t -> doSubscribe(t)).handleSuccess(Revisions::getPartitionSize);
		}

		@Override
		public void unseek(ResourceDescriptor topic, int partition) {
			assignments.get(topic)
					.testAndFail(r -> r.reset(partition) != Revision.UNKNOWN_OFFSET)
					.whenCompleteSuccessfully(r -> coldDelegate.updateAssignmentAndWait(assignments))
					.testAndFail(Revisions::isNonePartitionOffsetKnown)
					.whenCompleteSuccessfully(r -> assignments.remove(topic, r));
		}

		protected void unsubscribe(ResourceDescriptor topic, Runnable ifEmpty) {
			if (assignments.remove(topic) != null) {
				hotDelegate.updateSubscription(assignments.keySet());
				if (assignments.isEmpty()) {
					ifEmpty.run();
				}
			}
		}

		@Override
		public void unsubscribe(ResourceDescriptor topic) {
			unsubscribe(topic, this::hashCode);
		}
	}

	public interface Listener extends PartitionRebalanceListener {

		void onError(Throwable throwable);

		void onNextCold(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed);

		void onNextHot(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed);
	}

	private ColdChannel coldChannel;
	private HotChannel hotChannel;
	private final AtomicBoolean registered;

	public Channel(ColdChannel coldChannel, HotChannel hotChannel) {
		this.registered = new AtomicBoolean(false);
	}

	public Callback register(Listener listener) {
		if (registered.compareAndSet(false, true)) {
			if (coldChannel == hotChannel) {
				return new CombinedCallback(listener, coldChannel, hotChannel);
			} else {
				return new SeparatedCallback(listener, coldChannel, hotChannel);
			}
		} else {
			throw new IllegalStateException("Channel already registered");
		}
	}
}
