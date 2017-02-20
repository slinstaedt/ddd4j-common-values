package org.ddd4j.infrastructure.channel;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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

	public interface Callback extends Closeable {

		void seek(ResourceDescriptor topic, Revision revision);

		Promise<Integer> subscribe(ResourceDescriptor topic);

		void unseek(ResourceDescriptor topic, int partition);

		void unsubscribe(ResourceDescriptor topic);
	}

	public interface ColdCallback extends Closeable {

		void seek(ResourceDescriptor topic, Revision revision);

		void updateAssignment(Map<ResourceDescriptor, IntStream> assignments);

		default void updateAssignmentAndWait(Map<ResourceDescriptor, Promise<Revisions>> assignments) {
			Map<ResourceDescriptor, IntStream> values = assignments.entrySet().stream()
					.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().join().partitions()));
			updateAssignment(values);
		}
	}

	public static class CombinedCallback implements Callback {

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

			@Override
			public void onError(Throwable throwable) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onNext(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onPartitionsAssigned(ResourceDescriptor topic, int[] partitions) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onPartitionsRevoked(ResourceDescriptor topic, int[] partitions) {
				// TODO Auto-generated method stub

			}
		}

		private final Listener listener;
		private final ColdCallback coldDelegate;
		private final HotCallback hotDelegate;
		private final Map<ResourceDescriptor, Promise<Revisions>> assignments;

		private volatile boolean subscribed;

		public CombinedCallback(Listener listener, ColdChannel coldChannel, HotChannel hotChannel) {
			this.listener = Require.nonNull(listener);
			this.assignments = new ConcurrentHashMap<>();
			this.coldDelegate = null;// coldChannel.register(new ColdListener());
			this.hotDelegate = null;// hotChannel.register(new HotListener());
			this.subscribed = false;
		}

		@Override
		public void closeChecked() throws Exception {
			coldDelegate.closeChecked();
			hotDelegate.closeChecked();
		}

		private void doAssignAndSeek(ResourceDescriptor topic, Revision revision) {
			if (subscribed) {
				hotDelegate.updateSubscription(assignments.keySet());
			} else {
				coldDelegate.updateAssignmentAndWait(assignments);
			}
			coldDelegate.seek(topic, revision);
		}

		private Promise<Revisions> doFetchPartitionSize(ResourceDescriptor topic) {
			return hotDelegate.partitionSize(topic).sync().handleSuccess(Revisions::new);
		}

		private Promise<Revisions> doSubscribe(ResourceDescriptor topic) {
			subscribed = true;
			Set<ResourceDescriptor> topics = new HashSet<>(assignments.keySet());
			topics.add(topic);
			hotDelegate.updateSubscription(topics);
			return doFetchPartitionSize(topic);
		}

		@Override
		public void seek(ResourceDescriptor topic, Revision revision) {
			assignments.computeIfAbsent(topic, t -> doFetchPartitionSize(t)).whenCompleteSuccessfully(r -> r.update(revision))
					.whenCompleteSuccessfully(r -> doAssignAndSeek(topic, revision));
		}

		@Override
		public Promise<Integer> subscribe(ResourceDescriptor topic) {
			return assignments.computeIfAbsent(topic, t -> doSubscribe(t)).handleSuccess(Revisions::getPartitionSize);
		}

		@Override
		public void unseek(ResourceDescriptor topic, int partition) {
			assignments.get(topic).testAndFail(r -> r.reset(partition) != Revision.UNKNOWN_OFFSET)
					.whenCompleteSuccessfully(r -> coldDelegate.updateAssignmentAndWait(assignments))
					.testAndFail(Revisions::isNonePartitionOffsetKnown).whenCompleteSuccessfully(r -> assignments.remove(topic, r));
		}

		@Override
		public void unsubscribe(ResourceDescriptor topic) {
			if (assignments.remove(topic) != null) {
				hotDelegate.updateSubscription(assignments.keySet());
				if (assignments.isEmpty()) {
					subscribed = false;
				}
			}
		}
	}

	public interface HotCallback extends Closeable {

		Promise<Integer> partitionSize(ResourceDescriptor topic);

		void updateSubscription(Set<ResourceDescriptor> topics);
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
				return null;
			}
		} else {
			throw new IllegalStateException("Channel already registered");
		}
	}
}
